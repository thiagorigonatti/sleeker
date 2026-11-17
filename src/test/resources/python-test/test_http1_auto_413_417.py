"""
 # Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 # and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
"""

"""
This script tests HTTP/1.1 response ordering when automatic error responses
(413 Payload Too Large or 417 Expectation Failed) are generated for a
subsequent pipelined request while a previous request is still being processed.

TEST SCENARIO:
1. Establishes a TCP connection to the HTTP/1 server.
2. Sends a slow GET request to /slow5seconds (request A).
3. Immediately sends a second invalid POST request (request B).
4. Request B triggers an automatic HTTP error response:
   - HTTP 413 when Content-Length exceeds the configured maximum body size.
   - HTTP 417 when an unsupported Expect header is received.
5. Collects the responses received from the server.
6. Checks whether the automatic error response was sent before the
   response to the previous request.

EXPECTED BEHAVIOR:
- HTTP 200 from request A followed by HTTP 413 or HTTP 417 from request B.
- Alternatively, the server may conservatively close the connection
  without sending any response while request A is still pending.

The purpose is to verify whether the HTTP/1 server preserves response
ordering when automatic protocol-level error responses are generated
during HTTP/1.1 pipelining.

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

# Must match the server's http1.maxContentLength configuration.
MAX_CONTENT_LENGTH = 65535

# None = run both tests.
# 413  = run only the Payload Too Large test.
# 417  = run only the Expectation Failed test.
TEST_CASE = None

CONNECT_TIMEOUT = 5
CLIENT_TIMEOUT = 12

# Maximum elapsed time accepted for a conservative connection closure.
# The slow endpoint is expected to take approximately 8 seconds.
CONSERVATIVE_CLOSE_LIMIT = 7.0


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

    stream = context.wrap_socket(
        raw,
        server_hostname="localhost"
    )

    print("TLS enabled; ALPN:", stream.selected_alpn_protocol())

    return stream


# ============================================================
# TEST - Automatic HTTP Error Response Ordering
# ============================================================

def run_case(kind):

    # Request A: Complete slow GET request.
    # The server is expected to take approximately 8 seconds to process it.
    slow = (
        b"GET /slow5seconds HTTP/1.1\r\n"
        b"Host: localhost\r\n"
        b"\r\n"
    )

    # Request B: Invalid POST request.
    # The request content depends on the test case.
    if kind == 413:

        # Declare a Content-Length exceeding the configured server limit.
        # No request body is sent.
        invalid = (
            b"POST /http1_hello HTTP/1.1\r\n"
            b"Host: localhost\r\n"
            + f"Content-Length: {MAX_CONTENT_LENGTH + 1}\r\n".encode("ascii")
            + b"\r\n"
        )

    else:

        # Send an unsupported Expect header to trigger HTTP 417.
        invalid = (
            b"POST /http1_hello HTTP/1.1\r\n"
            b"Host: localhost\r\n"
            b"Expect: unsupported-value\r\n"
            b"Content-Length: 0\r\n"
            b"\r\n"
        )

    print(
        f"\n=== SLOW REQUEST A (8s) + AUTOMATIC HTTP {kind} RESPONSE B ==="
    )

    print("Sending both requests over the same TCP connection...")

    received = bytearray()
    closed = False

    with connect() as conn:

        start = time.monotonic()

        # Sends both pipelined requests in a single operation.
        sock_payload = slow + invalid
        conn.sendall(sock_payload)

        deadline = start + CLIENT_TIMEOUT

        while True:

            remaining = deadline - time.monotonic()

            if remaining <= 0:
                break

            conn.settimeout(remaining)

            try:
                chunk = conn.recv(16384)

            except socket.timeout:
                break

            except (
                ConnectionResetError,
                ssl.SSLEOFError,
                BrokenPipeError
            ) as exc:

                print(
                    f"[{time.monotonic() - start:.3f}s] "
                    f"Connection interrupted: {exc}"
                )

                closed = True
                break

            elapsed = time.monotonic() - start

            if not chunk:
                print(
                    f"[{elapsed:.3f}s] Connection closed"
                )

                closed = True
                break

            received.extend(chunk)

            print(
                f"[{elapsed:.3f}s] "
                f"Received {len(chunk)} bytes: {chunk[:180]!r}"
            )

    elapsed = time.monotonic() - start

    # Locate the first HTTP 200 response and the expected automatic error.
    first_200 = received.find(b"HTTP/1.1 200 OK")

    first_error = received.find(
        f"HTTP/1.1 {kind} ".encode()
    )

    print(f"Elapsed time: {elapsed:.3f}s | Connection closed: {closed}")

    print(f"HTTP 200 from A: {first_200 != -1} | HTTP {kind} from B: {first_error != -1}")

    # ========================================================
    # Result Validation
    # ========================================================

    # Fail if the automatic error response was received before
    # the response to the previous request A.
    if (
        first_error != -1
        and (first_200 == -1 or first_error < first_200)
    ):
        print(
            "FAILED: automatic response from request B "
            "overtook the response to request A."
        )

        return False

    # Accept a conservative connection closure when neither response
    # was sent and the connection was closed before request A completed.
    if (
        closed
        and first_error == -1
        and first_200 == -1
        and elapsed < CONSERVATIVE_CLOSE_LIMIT
    ):
        print(
            "PASSED: conservative policy "
            "(connection closed without an out-of-order response)."
        )

        return True

    # Accept responses when HTTP 200 from A precedes the expected
    # automatic error response from B.
    if (
        first_200 != -1
        and first_error != -1
        and first_200 < first_error
    ):
        print(
            "PASSED: response status ordering is correct; "
            "check complete framing to validate the response bodies."
        )

        return True

    print("INCONCLUSIVE: inspect the received data and server logs.")

    return False


# ============================================================
# Main
# ============================================================

def main():

    print("HTTP/1 Automatic Error Response Ordering Test")

    print(f"Target: {HOST}:{PORT}")
    print(f"Protocol: {'HTTPS (TLS)' if USE_TLS else 'HTTP (Plain)'}")
    print(f"Maximum content length: {MAX_CONTENT_LENGTH} bytes")
    print(f"Client timeout: {CLIENT_TIMEOUT}s")

    if TEST_CASE not in (None, 413, 417):
        raise ValueError("TEST_CASE must be None, 413, or 417.")

    cases = (
        [TEST_CASE]
        if TEST_CASE is not None
        else [413, 417]
    )

    results = []

    for code in cases:
        results.append(run_case(code))

    print("\n" + "=" * 50)
    print("FINAL SUMMARY")
    print("=" * 50)

    for code, success in zip(cases, results):
        print(
            f"HTTP {code}: "
            f"{'PASSED' if success else 'FAILED/INCONCLUSIVE'}"
        )

    return all(results)


# ============================================================
# Entry Point
# ============================================================

if __name__ == "__main__":

    try:
        success = main()
        raise SystemExit(0 if success else 1)

    except (OSError, ssl.SSLError) as error:
        print(f"\nFAILED: {type(error).__name__}: {error}")
        raise SystemExit(1)