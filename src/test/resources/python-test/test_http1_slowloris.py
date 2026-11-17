"""
 # Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 # and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
"""

"""
This script tests the HTTP/1 server's header timeout behavior using
a Slowloris-style connection.

TEST SCENARIO:
1. Establishes a TCP connection to the HTTP/1 server.
2. Sends an incomplete HTTP/1.1 GET request.
3. Keeps the request headers incomplete by sending one additional byte
   every 250 milliseconds.
4. Monitors the server for an HTTP 408 Request Timeout response.
5. Stops sending additional bytes when HTTP 408 is received or when
   a connection error occurs.
6. Verifies whether the server closes the connection after the timeout.

The purpose is to verify whether the HTTP/1 server properly detects
slow and incomplete HTTP headers and enforces the configured header timeout.

TLS can be enabled or disabled using the USE_TLS configuration variable.
"""


import socket
import ssl
import select
import time


# ============================================================
# Configuration
# ============================================================

HOST = "localhost"
PORT = 7777

USE_TLS = True

# Must match the server's configured header timeout.
HEADER_TIMEOUT = 4

# Sends one additional byte every 250 milliseconds.
INTERVAL = 0.25

# Maximum duration of the test.
MAX_DURATION = HEADER_TIMEOUT * 2 + 3

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
# TEST - HTTP/1 Slow Headers Timeout
# ============================================================

def test_slow_headers_timeout():

    with connect() as sock:

        # Intentionally incomplete HTTP request headers.
        # The final header terminator (\r\n\r\n) is never sent.
        initial = (
            b"GET /http1_hello HTTP/1.1\r\n"
            b"Host: localhost\r\n"
            b"X-Slow: "
        )

        start = time.monotonic()

        sock.sendall(initial)

        print("\n=== HTTP/1 SLOW HEADERS TIMEOUT TEST ===")

        print("Slowloris test started.")
        print(f"Expected header timeout: {HEADER_TIMEOUT}s")
        print(f"Interval between bytes: {INTERVAL}s")
        print("-" * 50)

        next_send = start + INTERVAL

        response = bytearray()

        sent = 0
        closed = False
        sending = True

        received_408_at = None

        while True:

            now = time.monotonic()
            elapsed = now - start

            # Stop the test when the maximum duration is reached.
            if elapsed >= MAX_DURATION:
                print("Maximum test duration reached.")
                break

            # Continue sending bytes without completing the headers.
            if sending and now >= next_send:

                try:
                    sock.sendall(b"a")
                    sent += 1

                    print(
                        f"[{elapsed:.3f}s] "
                        f"Byte {sent} sent"
                    )

                except OSError as error:

                    print(
                        f"[{elapsed:.3f}s] "
                        f"Sending interrupted: {error}"
                    )

                    sending = False

                next_send = now + INTERVAL

            # Check whether the TLS socket already has decrypted data
            # available before waiting for socket readability.
            pending = (
                USE_TLS and sock.pending() > 0
            )

            if pending:
                readable = [sock]

            else:
                readable, _, _ = select.select(
                    [sock],
                    [],
                    [],
                    0.05
                )

            if not readable:
                continue

            try:
                data = sock.recv(4096)

            except (OSError, ssl.SSLError) as error:

                print(f"Read error: {error}")
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

            print(
                f"\n[{elapsed:.3f}s] "
                "Response received:"
            )

            print(data.decode("latin-1"))

            # Stop sending additional bytes once HTTP 408 is received.
            if (
                b"HTTP/1.1 408 Request Timeout" in response
                and received_408_at is None
            ):

                received_408_at = elapsed
                sending = False

        # ====================================================
        # Final Result
        # ====================================================

        print("\n" + "=" * 50)
        print("FINAL RESULT")
        print("=" * 50)

        print(f"Slow bytes sent: {sent}")
        print(f"Connection closed: {closed}")

        if received_408_at is not None:

            print(
                f"HTTP 408 received after: "
                f"{received_408_at:.3f}s"
            )

            if closed:
                print(
                    "\nPASSED: header timeout triggered "
                    "and the connection was closed."
                )

            else:
                print(
                    "\nPARTIAL: HTTP 408 received, "
                    "but connection closure was not confirmed."
                )

        else:

            print(
                "\nFAILED: HTTP 408 Request Timeout "
                "was not observed."
            )


# ============================================================
# Main
# ============================================================

if __name__ == "__main__":

    print("HTTP/1 Slow Headers Timeout Test")
    print(f"Target: {HOST}:{PORT}")
    print(f"Protocol: {'HTTPS (TLS)' if USE_TLS else 'HTTP (Plain)'}")
    print(f"Expected header timeout: {HEADER_TIMEOUT}s")
    print(f"Byte transmission interval: {INTERVAL}s")
    print(f"Maximum test duration: {MAX_DURATION}s")

    try:
        test_slow_headers_timeout()

    except (OSError, ssl.SSLError) as error:
        print(f"\nFAILED: {type(error).__name__}: {error}")
        raise SystemExit(1)