"""
 # Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 # and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
"""

"""
This script tests HTTP/1.1 request body timeout behavior when a slow request
is followed by a second request with an incomplete body over the same
TCP connection (HTTP/1.1 Pipelining).

TEST SCENARIO:
1. Establishes a TCP connection to the HTTP/1 server.
2. Sends a complete GET request to /slow8seconds (request A).
3. Immediately sends a POST request to /http1_hello (request B).
4. Request B declares a Content-Length of 100 bytes but sends only 3 bytes.
5. The server processes request A while waiting for the remaining 97 bytes
   of request B.
6. The request body timeout of request B expires.
7. Verifies whether the server closes the connection without sending
   an HTTP 408 response out of order or an HTTP 200 response before closing.

The purpose is to verify how the HTTP/1 server handles request body timeouts
when a previous pipelined request is still being processed.

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

REQUEST_TIMEOUT = 4
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
# TEST - HTTP/1 Body Timeout with Pipelining
# ============================================================

def test_body_pipelining_timeout():

    # Request A: Complete GET request with slow processing.
    # The server is expected to take approximately 8 seconds to process it.
    request_a = (
        b"GET /slow8seconds HTTP/1.1\r\n"
        b"Host: localhost\r\n"
        b"\r\n"
    )

    # Request B: Complete headers with an incomplete request body.
    # Content-Length declares 100 bytes, but only 3 bytes are sent.
    # The remaining 97 bytes are intentionally withheld.
    request_b = (
        b"POST /http1_hello HTTP/1.1\r\n"
        b"Host: localhost\r\n"
        b"Content-Type: text/plain\r\n"
        b"Content-Length: 100\r\n"
        b"\r\n"
        b"abc"
    )

    payload = request_a + request_b

    with connect() as sock:

        print("\n=== HTTP/1 BODY TIMEOUT WITH PIPELINING TEST ===")

        print("Request A: GET /slow8seconds (8 seconds)")
        print("Request B: POST with incomplete body")
        print(f"Expected body timeout: {REQUEST_TIMEOUT}s")
        print("-" * 50)

        start = time.monotonic()
        deadline = start + CLIENT_TIMEOUT

        # Sends both requests over the same TCP connection.
        sock.sendall(payload)

        print("Two requests sent over the same connection.\n")

        responses = bytearray()

        closed = False
        reset = False

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

            except (
                ConnectionResetError,
                ssl.SSLEOFError
            ) as error:

                elapsed = time.monotonic() - start
                reset = True

                print(
                    f"[{elapsed:.3f}s] "
                    f"Connection interrupted: {error}"
                )

                break

            elapsed = time.monotonic() - start

            if not data:
                closed = True

                print(
                    f"[{elapsed:.3f}s] "
                    "Connection closed by server"
                )

                break

            responses.extend(data)

            print(f"[{elapsed:.3f}s] Received data:")
            print(repr(data))

        elapsed = time.monotonic() - start

    # Check whether the expected HTTP response was received.
    received_408 = (
        b"HTTP/1.1 408 Request Timeout"
        in responses
    )

    received_200 = (
        b"HTTP/1.1 200 OK"
        in responses
    )

    # Check whether any unexpected data was received.
    received_other = (
        len(responses) > 0
        and not received_408
        and not received_200
    )

    # Allow a timing tolerance of -0.5s to +1.5s.
    within_deadline = (
        REQUEST_TIMEOUT - 0.5
        <= elapsed
        <= REQUEST_TIMEOUT + 1.5
    )

    # ========================================================
    # Final Result
    # ========================================================

    print("\n" + "=" * 50)
    print("FINAL RESULT")
    print("=" * 50)

    print(f"Elapsed time: {elapsed:.3f}s")
    print(f"Connection closed: {closed}")
    print(f"Connection reset: {reset}")
    print(f"HTTP 408 received: {received_408}")
    print(f"HTTP 200 received: {received_200}")
    print(f"Other data received: {received_other}")

    if (
        closed
        and not received_408
        and not received_200
        and not received_other
        and within_deadline
    ):
        print(
            "\nPASSED: connection closed after the body timeout "
            "without sending an out-of-order HTTP 408 response."
        )

        return True

    elif received_408 and not received_200:
        print(
            "\nFAILED: HTTP 408 was sent before the previous "
            "request A response was completed."
        )

    elif received_200:
        print(
            "\nINCONCLUSIVE: HTTP 200 response received. "
            "Check the timeout behavior of request B."
        )

    else:
        print(
            "\nFAILED/INCONCLUSIVE: unexpected server behavior."
        )

    return False


# ============================================================
# Main
# ============================================================

if __name__ == "__main__":

    print("HTTP/1 Body Timeout with Pipelining Test")
    print(f"Target: {HOST}:{PORT}")
    print(f"Protocol: {'HTTPS (TLS)' if USE_TLS else 'HTTP (Plain)'}")
    print(f"Expected body timeout: {REQUEST_TIMEOUT}s")
    print(f"Client timeout: {CLIENT_TIMEOUT}s")

    try:
        success = test_body_pipelining_timeout()
        raise SystemExit(0 if success else 1)

    except (OSError, ssl.SSLError) as error:
        print(f"\nFAILED: {type(error).__name__}: {error}")
        raise SystemExit(1)