"""
 # Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 # and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
"""

"""
This script tests the HTTP/1 request body timeout behavior.

TEST SCENARIO:
1. Establishes a TCP connection to the HTTP/1 server.
2. Sends a POST request with complete HTTP headers.
3. Declares a Content-Length of 100 bytes.
4. Sends only 3 bytes of the request body ("abc").
5. Remains idle while the server waits for the remaining 97 bytes.
6. Waits for the server to return HTTP 408 Request Timeout.
7. Verifies that the connection is closed after the timeout.

The purpose is to verify whether the HTTP/1 server properly detects
incomplete request bodies, applies the configured request timeout,
returns HTTP 408, and closes the connection.

The server's requestTimeoutSeconds must be configured to match
REQUEST_TIMEOUT.

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
# TEST - HTTP/1 Request Body Timeout
# ============================================================

def test_body_timeout():
    payload = (
        b"POST /http1_hello HTTP/1.1\r\n"
        b"Host: localhost\r\n"
        b"Content-Type: text/plain\r\n"
        b"Content-Length: 100\r\n"
        b"\r\n"
        b"abc"
    )

    with connect() as sock:
        print("\n=== HTTP/1 REQUEST BODY TIMEOUT TEST ===")

        print(f"Expected timeout: {REQUEST_TIMEOUT}s")
        print("Declared Content-Length: 100 bytes")
        print("Body sent: 3 bytes")
        print("Remaining body: 97 bytes\n")

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

            except OSError as error:
                print("Connection error:", error)
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

    # Check the received HTTP response.
    received_408 = (
        b"HTTP/1.1 408 Request Timeout" in response
    )

    received_200 = (
        b"HTTP/1.1 200 OK" in response
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
    print(f"HTTP 408 received: {received_408}")
    print(f"HTTP 200 received: {received_200}")
    print(f"Connection closed: {closed}")

    if received_408 and not received_200 and closed:
        if within_deadline:
            print("\nPASSED: request body timeout is working.")
        else:
            print("\nPARTIAL: expected behavior detected,")
            print("but elapsed time was outside the allowed tolerance.")

    else:
        print("\nFAILED: unexpected server behavior.")

    return (
        received_408
        and not received_200
        and closed
        and within_deadline
    )


# ============================================================
# Main
# ============================================================

if __name__ == "__main__":
    print("HTTP/1 Request Body Timeout Test")
    print(f"Target: {HOST}:{PORT}")
    print(f"Protocol: {'HTTPS (TLS)' if USE_TLS else 'HTTP (Plain)'}")

    try:
        success = test_body_timeout()
        raise SystemExit(0 if success else 1)

    except (OSError, ssl.SSLError) as error:
        print(f"\nFAILED: {type(error).__name__}: {error}")
        raise SystemExit(1)