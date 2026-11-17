"""
 # Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 # and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
"""

"""
This script tests HTTP/1.1 header timeout behavior when a complete request
is followed by a second request with incomplete headers over the same
TCP connection.

TEST SCENARIO:
1. Establishes a TCP connection to the HTTP/1 server.
2. Sends a complete GET request to /slow3seconds.
3. Immediately sends a second GET request to /http1_hello with incomplete headers.
4. Waits for the server to process the first request and detect the header
   timeout of the second request.
5. Collects the responses received from the server.
6. Counts the HTTP 200 and HTTP 408 responses.

EXPECTED BEHAVIOR:
- One HTTP 200 response for the first request.
- One HTTP 408 response for the second request.

The purpose is to verify whether the HTTP/1 server correctly handles
incomplete headers in a subsequent request over a persistent connection.

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

# Must be greater than the server's configured header timeout.
CLIENT_TIMEOUT = 12

CONNECT_TIMEOUT = 5


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

    sock = context.wrap_socket(
        raw,
        server_hostname="localhost"
    )

    print("ALPN:", sock.selected_alpn_protocol())

    return sock


# ============================================================
# TEST - HTTP/1 Partial Header Timeout
# ============================================================

def test_partial_header_timeout():

    # Request A: Complete GET request.
    request_a = (
        b"GET /slow3seconds HTTP/1.1\r\n"
        b"Host: localhost\r\n"
        b"\r\n"
    )

    # Request B: GET request with intentionally incomplete headers.
    # The final CRLF and the header terminator are intentionally omitted.
    request_b = (
        b"GET /http1_hello HTTP/1.1\r\n"
        b"Host: incomplete"
    )

    payload = request_a + request_b

    responses = bytearray()

    with connect() as sock:

        print("\n=== HTTP/1 PARTIAL HEADER TIMEOUT TEST ===")

        print("Request A: GET /slow3seconds (complete)")
        print("Request B: GET /http1_hello (incomplete headers)")
        print(f"Client timeout: {CLIENT_TIMEOUT}s")
        print("-" * 50)

        start = time.monotonic()
        deadline = start + CLIENT_TIMEOUT

        # Sends both requests over the same TCP connection.
        sock.sendall(payload)

        print("Sending requests...\n")

        while True:

            remaining = deadline - time.monotonic()

            if remaining <= 0:
                print("Maximum client timeout reached.")
                break

            sock.settimeout(remaining)

            try:
                data = sock.recv(4096)

            except socket.timeout:
                print("Client read timeout.")
                break

            except OSError as error:
                print("Connection error:", error)
                break

            elapsed = time.monotonic() - start

            if not data:
                print(f"[{elapsed:.3f}s] Connection closed")
                break

            responses.extend(data)

            print(f"[{elapsed:.3f}s] Received:")
            print(data.decode("latin-1"))
            print("-" * 50)

    # Count the HTTP responses received from the server.
    count_200 = responses.count(b"HTTP/1.1 200 OK")

    count_408 = responses.count(
        b"HTTP/1.1 408 Request Timeout"
    )

    # ========================================================
    # Final Result
    # ========================================================

    print("\n" + "=" * 50)
    print("FINAL RESULT")
    print("=" * 50)

    print("HTTP 200 responses:", count_200)
    print("HTTP 408 responses:", count_408)

    if count_200 == 1 and count_408 == 1:
        print("\nPASSED: one HTTP 200 and one HTTP 408 response received.")

    else:
        print(
            "\nUNEXPECTED RESULT: check the server logs "
            "and header timeout configuration."
        )


# ============================================================
# Main
# ============================================================

if __name__ == "__main__":

    print("HTTP/1 Partial Header Timeout Test")
    print(f"Target: {HOST}:{PORT}")
    print(f"Protocol: {'HTTPS (TLS)' if USE_TLS else 'HTTP (Plain)'}")
    print(f"Client timeout: {CLIENT_TIMEOUT}s")

    try:
        test_partial_header_timeout()

    except (OSError, ssl.SSLError) as error:
        print(f"\nFAILED: {type(error).__name__}: {error}")
        raise SystemExit(1)