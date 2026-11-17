"""
 # Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 # and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
"""

"""
This script tests HTTP/1.1 behavior when a slow request is followed by
a second request with incomplete headers over the same TCP connection.

TEST 1 - Control: Complete slow request
Establishes a connection, sends a complete GET request to the slow endpoint,
and verifies that the server returns HTTP 200 with the expected response body.

TEST 2 - Slow request followed by incomplete headers:
1. Sends a complete slow GET request (request A).
2. Immediately sends a second GET request (request B) with incomplete headers.
3. Waits for the server to close the connection when the header timeout expires.
4. Checks whether the connection was closed within the expected time window.
5. Verifies that no HTTP 408 or HTTP 200 response was received before closing.

The purpose is to verify how the HTTP/1 server handles header timeouts
on a subsequent pipelined request while a previous request is still being
processed.

TLS can be enabled or disabled using the USE_TLS configuration variable.
"""

import socket
import ssl
import time


# ============================================================
# Configuration
# ============================================================

HOST = "localhost"
PORT = 7777

USE_TLS = True

PATH = "/slow8seconds"

CONNECT_TIMEOUT = 5
HEADER_TIMEOUT = 4.0
SLOW_SECONDS = 8.0


# ============================================================
# Connection
# ============================================================

def connect():
    raw = socket.create_connection(
        (HOST, PORT),
        timeout=CONNECT_TIMEOUT
    )

    if not USE_TLS:
        return raw

    context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    context.check_hostname = False  # For local development only.
    context.verify_mode = ssl.CERT_NONE
    context.set_alpn_protocols(["http/1.1"])

    try:
        wrapped = context.wrap_socket(
            raw,
            server_hostname="localhost"
        )
    except Exception:
        raw.close()
        raise

    protocol = wrapped.selected_alpn_protocol()
    print("ALPN:", protocol)

    if protocol not in (None, "http/1.1"):
        wrapped.close()
        raise RuntimeError("TLS connection did not negotiate HTTP/1.1")

    return wrapped


# ============================================================
# HTTP Request
# ============================================================

def request(path: str) -> bytes:
    return (
        f"GET {path} HTTP/1.1\r\n"
        "Host: localhost\r\n"
        "Connection: keep-alive\r\n"
        "\r\n"
    ).encode("ascii")


# ============================================================
# TEST 1 - Control: Complete slow request
# ============================================================

def one_slow_request():
    print("\n=== CONTROL: One complete slow request ===")

    with connect() as sock:
        sock.settimeout(SLOW_SECONDS + 7)

        start = time.monotonic()

        sock.sendall(request(PATH))

        received = bytearray()

        while True:
            try:
                data = sock.recv(8192)

            except socket.timeout:
                print("FAILED: server did not send a response within the timeout.")
                return False

            if not data:
                print("FAILED: connection closed before the response was completed.")
                return False

            received.extend(data)

            if b"\r\n\r\n" not in received:
                continue

            header, body = received.split(b"\r\n\r\n", 1)

            length = None

            for line in header.split(b"\r\n")[1:]:
                if line.lower().startswith(b"content-length:"):
                    length = int(line.split(b":", 1)[1].strip())

            if length is not None and len(body) >= length:
                break

            if length is None:
                print("INCONCLUSIVE: control endpoint did not send Content-Length.")
                return False

    elapsed = time.monotonic() - start

    status = header.split(b"\r\n", 1)[0]

    print(f"[{elapsed:.3f}s] {status.decode('latin-1')}")
    print("Body:", bytes(body[:length]))

    ok = (
        status.startswith(b"HTTP/1.1 200 ")
        and body[:length] == b"Slow 8 seconds response"
    )

    if ok:
        print("PASSED: slow endpoint responded correctly.")
    else:
        print("FAILED: check the /slow8seconds endpoint before running the next test.")

    return ok


# ============================================================
# TEST 2 - Slow request followed by incomplete headers
# ============================================================

def pipelined_partial_header():
    print("\n=== TEST: Slow request A + incomplete headers B ===")

    first = request(PATH)

    # The second request intentionally has incomplete headers.
    second = b"GET /segunda HTTP/1.1\r\nHost: incomplete"

    payload = first + second

    received = bytearray()

    disconnected = False
    disconnected_by_reset = False

    start = time.monotonic()
    deadline = start + HEADER_TIMEOUT + 5

    with connect() as sock:
        sock.sendall(payload)

        print("Sent: complete GET /slow8seconds + incomplete GET /segunda")
        print("Waiting for connection closure or response...")

        while time.monotonic() < deadline:

            sock.settimeout(
                min(0.5, max(0.01, deadline - time.monotonic()))
            )

            try:
                data = sock.recv(8192)

            except socket.timeout:
                continue

            except (ConnectionResetError, ssl.SSLError) as exc:
                print(
                    f"[{time.monotonic() - start:.3f}s] "
                    f"Connection interrupted: {exc}"
                )

                disconnected = True
                disconnected_by_reset = True
                break

            if not data:
                print(
                    f"[{time.monotonic() - start:.3f}s] "
                    "Connection closed"
                )

                disconnected = True
                break

            received.extend(data)

            print(
                f"[{time.monotonic() - start:.3f}s] "
                f"Received bytes: {data[:240]!r}"
            )

    elapsed = time.monotonic() - start

    has_408 = b"HTTP/1.1 408 " in received
    has_200 = b"HTTP/1.1 200 " in received

    print("\nRESULT")

    print(f"Duration: {elapsed:.3f}s")
    print("Connection closed:", disconnected)
    print("Connection reset:", disconnected_by_reset)
    print("HTTP 408 received:", has_408)
    print("HTTP 200 received:", has_200)

    # Allows timing variations caused by local scheduling and timers.
    # Does not verify the internal reason for the connection closure.
    reasonable_time = (
        max(0.0, HEADER_TIMEOUT - 1.5)
        <= elapsed
        <= HEADER_TIMEOUT + 2.0
    )

    if disconnected and reasonable_time and not has_408 and not has_200:
        print(
            "PASSED: connection closed near the header timeout "
            "without sending a response before the previous request."
        )
        return True

    if has_408:
        print(
            "FAILED: HTTP 408 was sent while the previous slow response "
            "was still pending."
        )

    elif not disconnected:
        print("FAILED: connection was not closed within the expected time.")

    elif has_200:
        print(
            "INCONCLUSIVE: HTTP 200 was received before connection closure; "
            "check the actual endpoint processing time."
        )

    else:
        print(
            "INCONCLUSIVE: connection closed outside the expected time window; "
            "check the server logs."
        )

    return False


# ============================================================
# Main
# ============================================================

def main():
    print("HTTP/1 Header Timeout and Pipelining Test")

    print(f"Target: {HOST}:{PORT}")
    print(f"Protocol: {'HTTPS (TLS)' if USE_TLS else 'HTTP (Plain)'}")
    print(f"Endpoint: {PATH}")
    print(f"Header timeout: {HEADER_TIMEOUT}s")
    print(f"Slow request timeout: {SLOW_SECONDS}s")

    control_ok = one_slow_request()

    if not control_ok:
        raise SystemExit(
            "Aborted: fix the slow endpoint before testing HTTP 408."
        )

    test_ok = pipelined_partial_header()

    raise SystemExit(0 if test_ok else 1)


if __name__ == "__main__":
    main()