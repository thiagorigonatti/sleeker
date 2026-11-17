"""
 # Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 # and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
"""

"""
This script tests HTTP/1.1 pipelining response ordering using a large file
download followed by a short response over the same TCP connection.

TEST SCENARIO:
1. Sends a GET request for a large file with Connection: keep-alive.
2. Immediately sends a second GET request for /http1_hello with
   Connection: close.
3. Reads the first response slowly, simulating a client consuming the
   response body at a reduced rate.
4. Validates the first response using its HTTP status, content length,
   and SHA-256 hash.
5. Reads and validates the second response.

The purpose is to verify that the HTTP/1 server preserves response ordering
and data integrity when handling pipelined requests involving a large
streaming response followed by a short response.

TLS can be enabled or disabled using the USE_TLS configuration variable.

The server must publish the absolute file path configured in FILE_PATH.
"""

import hashlib
import os
import socket
import ssl
import sys
import time


HOST = "localhost"
PORT = 7777

USE_TLS = True

CLIENT_TIMEOUT = 30

FILE_PATH = "/home/thiagorigonatti/IdeaProjects/sleeker/src/test/resources/testing-files/test-file-1mb.bin"

BLOCK_SIZE = 4096
READ_DELAY_SECONDS = 0.002  # Delays reading the response body.


def read_line(stream, limit=16384):
    line = stream.readline(limit + 1)

    if not line:
        raise EOFError("Connection closed while reading an HTTP line")

    if len(line) > limit or not line.endswith(b"\r\n"):
        raise ValueError("HTTP line too long or invalid CRLF termination")

    return line[:-2]


def response_headers(stream):
    status = read_line(stream).decode("latin-1")

    if not status.startswith("HTTP/1."):
        raise ValueError(f"Invalid status line: {status!r}")

    parts = status.split(" ", 2)

    if len(parts) < 2 or not parts[1].isdigit():
        raise ValueError(f"Invalid status code: {status!r}")

    headers = {}

    for _ in range(200):
        line = read_line(stream)

        if not line:
            break

        if b":" not in line:
            raise ValueError(f"Invalid header: {line!r}")

        key, value = line.split(b":", 1)
        name = key.decode("ascii").lower()

        if name in headers:
            # Prevents interpreting responses with conflicting framing headers.
            if name in ("content-length", "transfer-encoding"):
                raise ValueError(f"Duplicate framing header: {name}")

        headers[name] = value.decode("latin-1").strip()

    else:
        raise ValueError("Excessive number of response headers")

    return int(parts[1]), status, headers


def read_exact(stream, n):
    data = stream.read(n)

    if data is None or len(data) != n:
        raise EOFError(f"Response interrupted: expected {n} bytes")

    return data


def response_body(stream, headers, *, slow=False):
    size = 0
    digest = hashlib.sha256()
    preview = bytearray()

    def consume(n):
        nonlocal size

        while n:
            data = read_exact(stream, min(BLOCK_SIZE, n))

            digest.update(data)
            size += len(data)

            if len(preview) < 256:
                preview.extend(data[:256 - len(preview)])

            n -= len(data)

            if slow:
                time.sleep(READ_DELAY_SECONDS)

    te = headers.get("transfer-encoding", "").lower()

    if "chunked" in [t.strip() for t in te.split(",")]:
        framing = "chunked"

        while True:
            chunk_header = read_line(stream).split(b";", 1)[0]

            try:
                chunk_size = int(chunk_header, 16)
            except ValueError as exc:
                raise ValueError(
                    f"Invalid chunk size: {chunk_header!r}"
                ) from exc

            if chunk_size == 0:
                for _ in range(200):
                    if not read_line(stream):
                        break
                else:
                    raise ValueError("Trailers were not properly terminated")

                break

            consume(chunk_size)

            if read_exact(stream, 2) != b"\r\n":
                raise ValueError("Invalid chunk termination")

    elif "content-length" in headers:
        framing = "content-length"
        consume(int(headers["content-length"]))

    else:
        raise ValueError(
            "Response without Content-Length or Transfer-Encoding: chunked; "
            "cannot safely delimit two persistent responses"
        )

    return size, digest.hexdigest(), bytes(preview), framing


def sha256_file(path):
    h = hashlib.sha256()

    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)

    return h.hexdigest()


def main():
    file_path = os.path.abspath(FILE_PATH)

    if not os.path.isfile(file_path):
        raise ValueError(f"File does not exist: {file_path}")

    expected_size = os.path.getsize(file_path)

    if expected_size < 1024 * 1024:
        raise ValueError(
            "Use a file containing at least 1 MiB to exercise streaming"
        )

    expected_hash = sha256_file(file_path)

    # The server router uses the absolute file path as the URI path.
    target = file_path.encode("ascii")

    first = (
        b"GET " + target + b" HTTP/1.1\r\n"
        b"Host: localhost\r\n"
        b"Connection: keep-alive\r\n"
        b"\r\n"
    )

    second = (
        b"GET /http1_hello HTTP/1.1\r\n"
        b"Host: localhost\r\n"
        b"Connection: close\r\n"
        b"\r\n"
    )

    raw = socket.create_connection(
        (HOST, PORT),
        timeout=CLIENT_TIMEOUT
    )

    raw.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 16384)

    if USE_TLS:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
        context.set_alpn_protocols(["http/1.1"])

        sock = context.wrap_socket(
            raw,
            server_hostname="localhost"
        )

        protocol = sock.selected_alpn_protocol()

        if protocol not in (None, "http/1.1"):
            raise RuntimeError(
                f"Server selected unexpected ALPN protocol: {protocol}"
            )

        print("TLS: enabled, ALPN:", protocol)

    else:
        sock = raw
        print("TLS: disabled (for the example handler, use TLS)")

    with sock:
        sock.settimeout(CLIENT_TIMEOUT)

        start = time.monotonic()

        # Sends both pipelined requests in a single operation.
        sock.sendall(first + second)

        print("Sent: GET file + GET /http1_hello over the SAME connection")
        print(f"File: {file_path} ({expected_size:,} bytes)")

        print(
            f"Slow reading: {BLOCK_SIZE} bytes / "
            f"{READ_DELAY_SECONDS * 1000:.0f} ms (approximately)"
        )

        with sock.makefile("rb", buffering=4096) as stream:

            status1, line1, hdr1 = response_headers(stream)

            print(
                f"[{time.monotonic()-start:.3f}s] "
                f"First response: {line1}"
            )

            n1, hash1, _, framing1 = response_body(
                stream,
                hdr1,
                slow=True
            )

            print(
                f"[{time.monotonic()-start:.3f}s] "
                f"First response completed: {n1:,} bytes ({framing1})"
            )

            status2, line2, hdr2 = response_headers(stream)

            print(
                f"[{time.monotonic()-start:.3f}s] "
                f"Second response: {line2}"
            )

            n2, _, body2, framing2 = response_body(stream, hdr2)

            print(
                f"[{time.monotonic()-start:.3f}s] "
                f"Second response body: {body2!r} ({framing2})"
            )

    errors = []

    if status1 != 200:
        errors.append(
            f"First response should be 200, received {status1}"
        )

    if n1 != expected_size:
        errors.append(
            f"Incorrect file size ({n1} != {expected_size})"
        )

    if hash1 != expected_hash:
        errors.append(
            "SHA-256 of the first response differs from the original file"
        )

    if status2 != 200:
        errors.append(
            f"Second response should be 200, received {status2}"
        )

    if body2 != b"Hello from HTTP/1.1" or n2 != 19:
        errors.append(
            "Second response does not match the expected text"
        )

    if errors:
        print("\nFAILED:")

        for error in errors:
            print(" -", error)

        return 1

    print(
        "\nPASSED: file response is complete and intact; "
        "the text response followed without interleaving."
    )

    print(
        "NOTE: This test validates the order of received bytes; "
        "it does not prove that handler B waited for handler A's future."
    )

    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())

    except (OSError, ValueError, EOFError, ssl.SSLError) as exc:
        print(
            f"\nFAILED: {type(exc).__name__}: {exc}",
            file=sys.stderr
        )
        sys.exit(1)