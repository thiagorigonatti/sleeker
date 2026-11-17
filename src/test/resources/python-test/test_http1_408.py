"""
 # Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 # and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
"""

"""
This script tests the HTTP/1 server's header timeout behavior when a client
sends an incomplete HTTP/1.1 request and stops transmitting data.

TEST SCENARIO:
1. Establishes a TCP connection to the HTTP/1 server.
2. Sends a GET request with intentionally incomplete headers.
3. Stops sending data without completing the HTTP headers.
4. Waits for the server to detect the header timeout.
5. Verifies whether the server returns HTTP 408 Request Timeout.
6. Verifies whether the server closes the connection within the expected
   time window.

The purpose is to verify whether the HTTP/1 server properly detects
incomplete HTTP headers, enforces the configured header timeout,
returns HTTP 408, and closes the connection.

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

# Must match the server's configured header timeout.
HEADER_TIMEOUT = 4

# Maximum time the client waits for the server response.
CLIENT_TIMEOUT = 10

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
# TEST - HTTP/1 Header Timeout
# ============================================================

def test_header_timeout():

    # Intentionally incomplete HTTP/1.1 request.
    # The final CRLF and the header terminator are intentionally omitted.
    payload = (
        b"GET /http1_hello HTTP/1.1\r\n"
        b"Host: localhost"
    )

    with connect() as sock:

        print("\n=== HTTP/1 HEADER TIMEOUT TEST ===")

        print(f"Expected header timeout: {HEADER_TIMEOUT}s")
        print("Sending incomplete HTTP headers...\n")

        start = time.monotonic()
        deadline = start + CLIENT_TIMEOUT

        sock.sendall(payload)

        response = bytearray()
        closed = False

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

            elapsed = time.monotonic() - start

            if not data:
                closed = True

                print(
                    f"[{elapsed:.3f}s] "
                    "Connection closed by server"
                )

                break

            response.extend(data)

            print(f"[{elapsed:.3f}s] Received:")
            print(data.decode("latin-1"))

        elapsed = time.monotonic() - start

    # Check whether the server returned the expected HTTP 408 response.
    received_408 = response.startswith(
        b"HTTP/1.1 408 Request Timeout\r\n"
    )

    # Allow a timing tolerance of -0.5s to +1.5s.
    within_deadline = (
        HEADER_TIMEOUT - 0.5
        <= elapsed
        <= HEADER_TIMEOUT + 1.5
    )

    # ========================================================
    # Final Result
    # ========================================================

    print("\n" + "=" * 50)
    print("FINAL RESULT")
    print("=" * 50)

    print(f"Elapsed time: {elapsed:.3f}s")
    print(f"HTTP 408 received: {received_408}")
    print(f"Connection closed: {closed}")

    if received_408 and closed and within_deadline:

        print(
            "\nPASSED: HTTP 408 was received "
            "and the connection was closed within the expected time."
        )

    elif received_408 and closed:

        print(
            "\nPARTIAL: HTTP 408 received and connection closed, "
            "but elapsed time was outside the allowed tolerance."
        )

    else:

        print(
            "\nFAILED: unexpected server behavior."
        )

    return received_408 and closed and within_deadline


# ============================================================
# Main
# ============================================================

if __name__ == "__main__":

    print("HTTP/1 Header Timeout Test")
    print(f"Target: {HOST}:{PORT}")
    print(f"Protocol: {'HTTPS (TLS)' if USE_TLS else 'HTTP (Plain)'}")
    print(f"Expected header timeout: {HEADER_TIMEOUT}s")
    print(f"Client timeout: {CLIENT_TIMEOUT}s")

    try:
        success = test_header_timeout()
        raise SystemExit(0 if success else 1)

    except (OSError, ssl.SSLError) as error:
        print(f"\nFAILED: {type(error).__name__}: {error}")
        raise SystemExit(1)