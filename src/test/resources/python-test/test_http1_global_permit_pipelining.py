"""
 # Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 # and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
"""

"""
HTTP/1.1 Global Permit / Pipelining Test

Validates that a shared Http1BoundedExecutor does not prematurely
close a connection when the next pipelined request is scheduled.

Server requirements:
    maxConcurrentRequests = 1
    maxRequests >= 2
    maxPipelineDepth >= 2

Expected behavior:
    1. The first request runs for approximately 3 seconds.
    2. The second request waits in the same connection's serial queue.
    3. Both responses return HTTP 200 in the correct order.
    4. The connection must not close between responses.

TLS is optional via USE_TLS.
Uses only the Python standard library.
"""

import socket
import ssl
import sys
import time


# ============================================================
# Configuration
# ============================================================

HOST = "localhost"
PORT = 7777

USE_TLS = True

CONNECT_TIMEOUT = 5
CLIENT_TIMEOUT = 9
READ_TIMEOUT = 0.5

SLOW_PATH = "/slow3seconds"
FAST_PATH = "/http1_hello"

EXPECTED_SLOW_BODY = b"Slow 3 seconds response"
EXPECTED_FAST_BODY = b"Hello from HTTP/1.1"

# Required server configuration (informational only).
REQUIRED_MAX_CONCURRENT_REQUESTS = 1
REQUIRED_MIN_MAX_REQUESTS = 2


# ============================================================
# Connection
# ============================================================

def create_connection():
    """
    Create an HTTP/1.1 connection using TCP or TLS.
    """

    raw = socket.create_connection(
        (HOST, PORT),
        timeout=CONNECT_TIMEOUT
    )

    if not USE_TLS:
        return raw

    try:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)

        # Accept self-signed certificates in local tests.
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE

        context.set_alpn_protocols(["http/1.1"])

        sock = context.wrap_socket(
            raw,
            server_hostname=HOST
        )

        selected = sock.selected_alpn_protocol()

        if selected not in (None, "http/1.1"):
            sock.close()
            raise RuntimeError(
                f"Unexpected ALPN protocol: {selected}"
            )

        return sock

    except Exception:
        raw.close()
        raise


# ============================================================
# HTTP response reader
# ============================================================

def read_more(sock, existing, deadline):
    """
    Read more bytes without exceeding the global deadline.
    """

    remaining = deadline - time.monotonic()

    if remaining <= 0:
        raise TimeoutError(
            "Timed out waiting for both HTTP responses"
        )

    sock.settimeout(min(READ_TIMEOUT, remaining))

    try:
        chunk = sock.recv(65536)

    except socket.timeout:
        return existing

    if not chunk:
        raise ConnectionError(
            "Server closed the connection before "
            "both responses completed"
        )

    return existing + chunk


def read_response(sock, pending, deadline):
    """
    Read one HTTP/1.1 response.

    Uses Content-Length to determine the response body size.

    Preserves extra bytes because multiple pipelined responses
    may arrive together in the same TCP read.
    """

    # Read response headers.
    while b"\r\n\r\n" not in pending:
        pending = read_more(
            sock,
            pending,
            deadline
        )

    header, body_with_extra = pending.split(
        b"\r\n\r\n",
        1
    )

    lines = header.split(b"\r\n")

    try:
        # Parse HTTP status line.
        parts = lines[0].decode("ascii").split()

        if len(parts) < 2:
            raise ValueError("Invalid HTTP status line")

        version = parts[0]
        status = int(parts[1])

        if version != "HTTP/1.1":
            raise ValueError(
                f"Unexpected HTTP version: {version}"
            )

        # Parse HTTP headers.
        headers = {}

        for line in lines[1:]:
            name, value = line.split(b":", 1)

            headers[name.strip().lower()] = (
                value.strip()
            )

        # This test expects fixed-size responses.
        if b"content-length" not in headers:
            raise ValueError(
                "Missing Content-Length header"
            )

        length = int(headers[b"content-length"])

        if length < 0:
            raise ValueError(
                "Negative Content-Length"
            )

    except (ValueError, IndexError) as error:
        raise RuntimeError(
            f"Could not parse HTTP response: {error}"
        ) from error

    # Read the complete body.
    while len(body_with_extra) < length:
        body_with_extra = read_more(
            sock,
            body_with_extra,
            deadline
        )

    body = body_with_extra[:length]

    # Preserve bytes from subsequent responses.
    remaining = body_with_extra[length:]

    return status, body, remaining


# ============================================================
# Test
# ============================================================

def run_test():
    """
    Send two pipelined HTTP/1.1 requests and verify that
    both responses complete successfully in order.
    """

    print("=" * 60)
    print("HTTP/1.1 GLOBAL PERMIT / PIPELINING TEST")
    print("=" * 60)

    print(f"Target: {HOST}:{PORT}")
    print(f"TLS: {USE_TLS}")

    print(
        "Required server config: "
        f"maxConcurrentRequests={REQUIRED_MAX_CONCURRENT_REQUESTS}; "
        f"maxRequests>={REQUIRED_MIN_MAX_REQUESTS}"
    )

    print(
        f"Pipeline: {SLOW_PATH} -> {FAST_PATH}"
    )

    print(
        "Expected: HTTP 200 + HTTP 200, "
        "on the same connection"
    )

    print()

    # Build both requests on a single connection.
    requests = (
        f"GET {SLOW_PATH} HTTP/1.1\r\n"
        f"Host: {HOST}\r\n"
        f"Connection: keep-alive\r\n"
        f"\r\n"

        f"GET {FAST_PATH} HTTP/1.1\r\n"
        f"Host: {HOST}\r\n"
        f"Connection: keep-alive\r\n"
        f"\r\n"
    ).encode("ascii")

    statuses = []
    bodies = []

    start = time.monotonic()

    try:
        with create_connection() as sock:

            start = time.monotonic()

            deadline = start + CLIENT_TIMEOUT

            # Send both requests immediately.
            # The second request must wait while the first
            # handler occupies the only global executor slot.
            sock.sendall(requests)

            buffer = b""

            for index in range(2):

                try:
                    status, body, buffer = read_response(
                        sock,
                        buffer,
                        deadline
                    )

                except (
                    ConnectionError,
                    TimeoutError,
                    OSError,
                    RuntimeError
                ) as error:

                    elapsed = (
                        time.monotonic() - start
                    )

                    print(
                        f"FAILED after {elapsed:.3f}s: {error}"
                    )

                    print(
                        f"Responses received: {statuses}"
                    )

                    print(
                        "Possible premature connection close "
                        "or timeout while waiting for a "
                        "global executor permit."
                    )

                    return 1

                statuses.append(status)
                bodies.append(body)

                elapsed = (
                    time.monotonic() - start
                )

                print(
                    f"[{elapsed:.3f}s] "
                    f"Response {index + 1}: "
                    f"HTTP {status}, "
                    f"body={body[:100]!r}"
                )

        # Validate HTTP status codes.
        if statuses != [200, 200]:
            print()
            print(
                f"FAILED: Expected [200, 200], "
                f"received {statuses}"
            )
            return 1

        # Validate response order and bodies.
        if bodies[0] != EXPECTED_SLOW_BODY:
            print()
            print(
                "FAILED: First response body "
                "does not match the slow endpoint."
            )
            print(
                f"Expected: {EXPECTED_SLOW_BODY!r}"
            )
            print(
                f"Received: {bodies[0]!r}"
            )
            return 1

        if bodies[1] != EXPECTED_FAST_BODY:
            print()
            print(
                "FAILED: Second response body "
                "does not match the fast endpoint."
            )
            print(
                f"Expected: {EXPECTED_FAST_BODY!r}"
            )
            print(
                f"Received: {bodies[1]!r}"
            )
            return 1

        print()
        print(
            "PASSED: Both pipelined responses "
            "were delivered in order."
        )

        print(
            "PASSED: No premature connection close "
            "caused by global permit exhaustion."
        )

        return 0

    except (
        OSError,
        ssl.SSLError,
        RuntimeError,
        UnicodeEncodeError
    ) as error:

        print()
        print(
            f"FAILED: Connection/setup error: {error}"
        )

        return 2


# ============================================================
# Entry point
# ============================================================

if __name__ == "__main__":
    sys.exit(run_test())
