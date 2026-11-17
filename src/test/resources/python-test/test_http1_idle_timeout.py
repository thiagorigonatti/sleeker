"""
 # Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 # and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
"""

"""
This script tests the idle connection timeout behavior of the HTTP/1 server
in two different scenarios:

TEST 1 - TCP connection without a request:
Establishes a TCP connection to the server without sending any HTTP request
and waits for the server to close the connection due to inactivity.

TEST 2 - HTTP/1.1 Keep-Alive connection:
Establishes a TCP connection, sends an HTTP/1.1 GET request with the
Connection: keep-alive header, reads the complete HTTP response, and then
remains idle without sending any further requests until the server closes
the connection due to inactivity.

Both tests measure the elapsed time until the connection is closed and
consider the test successful if the server closes the connection before
the client's read timeout (15 seconds).

The purpose is to verify whether the HTTP/1 server properly closes idle
connections, both before receiving an HTTP request and after completing
an HTTP response on a persistent connection.

The server's idleTimeoutSeconds must be configured to a value lower than
CLIENT_TIMEOUT, which is 15 seconds in this case.

TLS can be enabled or disabled using the USE_TLS configuration variable.
"""

import socket
import ssl
import time

HOST = "localhost"
PORT = 7777

USE_TLS = True

CLIENT_TIMEOUT = 15


def connect():
    raw_sock = socket.create_connection(
        (HOST, PORT),
        timeout=CLIENT_TIMEOUT
    )

    if USE_TLS:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
        context.set_alpn_protocols(["http/1.1"])

        sock = context.wrap_socket(
            raw_sock,
            server_hostname=HOST
        )
    else:
        sock = raw_sock

    sock.settimeout(CLIENT_TIMEOUT)
    return sock


def wait_for_close(sock):
    start = time.monotonic()

    try:
        while True:
            data = sock.recv(4096)

            if not data:
                elapsed = time.monotonic() - start
                print(f"Connection closed after {elapsed:.3f}s")
                return elapsed

            print("Unexpected data:", repr(data))

    except socket.timeout:
        print("FAILED: connection was not closed within the timeout.")
        return None


def test_no_request():
    print("\nTEST 1 - Connection without a request")

    with connect() as sock:
        elapsed = wait_for_close(sock)

    if elapsed is not None:
        print("PASSED: idle connection was closed.")


def read_response(sock):
    received = bytearray()

    while b"\r\n\r\n" not in received:
        chunk = sock.recv(4096)

        if not chunk:
            raise RuntimeError(
                "Connection closed before headers were received"
            )

        received.extend(chunk)

    headers, body = bytes(received).split(b"\r\n\r\n", 1)

    length = 0

    for line in headers.split(b"\r\n"):
        if line.lower().startswith(b"content-length:"):
            length = int(line.split(b":", 1)[1].strip())

    while len(body) < length:
        chunk = sock.recv(4096)

        if not chunk:
            raise RuntimeError(
                "Connection closed before the response body was received"
            )

        body += chunk

    print(headers.decode("latin-1"))
    print("Body:", body[:length].decode("utf-8"))


def test_keep_alive():
    print("\nTEST 2 - Idle Keep-Alive connection")

    with connect() as sock:
        sock.sendall(
            b"GET /http1_hello HTTP/1.1\r\n"
            b"Host: localhost\r\n"
            b"Connection: keep-alive\r\n"
            b"\r\n"
        )

        read_response(sock)

        print("\nWaiting for keep-alive timeout...")
        elapsed = wait_for_close(sock)

    if elapsed is not None:
        print("PASSED: idle keep-alive connection was closed.")


if __name__ == "__main__":
    print("HTTP/1 Idle Timeout Test")
    print(f"Protocol: {'HTTPS (TLS)' if USE_TLS else 'HTTP (Plain)'}")
    print(f"Target: {HOST}:{PORT}")

    test_no_request()
    test_keep_alive()