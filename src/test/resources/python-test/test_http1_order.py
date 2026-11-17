"""
 # Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 # and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
"""

"""
This script tests HTTP/1.1 pipelining response ordering using two
consecutive requests sent over the same TCP connection.

TEST SCENARIO:
1. Sends a slow GET request to /slow5seconds, which is expected to take
   approximately 5 seconds to process.
2. Immediately sends a second GET request to /invalid%00Path,
   containing an invalid URL path (%00).
3. Receives and collects the server responses.
4. Extracts the HTTP status codes and verifies whether the responses
   are returned in the same order as the requests.

EXPECTED RESPONSE ORDER:
HTTP/1.1 200 OK  - Response to the first (slow) request.
HTTP/1.1 400     - Response to the second (invalid path) request.

TLS can be enabled or disabled using the USE_TLS configuration variable.
"""

import socket
import ssl
import time
import re

HOST = "localhost"
PORT = 7777

USE_TLS = True

payload = (
    b"GET /slow5seconds HTTP/1.1\r\n"
    b"Host: localhost\r\n"
    b"\r\n"
    b"GET /invalid%00Path HTTP/1.1\r\n"
    b"Host: localhost\r\n"
    b"\r\n"
)


def main():
    raw = socket.create_connection((HOST, PORT), timeout=5)

    if USE_TLS:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
        context.set_alpn_protocols(["http/1.1"])

        sock = context.wrap_socket(
            raw, server_hostname="localhost"
        )
    else:
        sock = raw

    with sock:
        sock.settimeout(0.5)
        start = time.monotonic()
        responses = bytearray()

        sock.sendall(payload)

        print("Two requests sent over the same connection.")
        print("First request: slow (5 seconds)")
        print("Second request: invalid path (%00)")
        print("-" * 50)

        while time.monotonic() - start < 8:
            try:
                data = sock.recv(4096)
            except socket.timeout:
                continue

            elapsed = time.monotonic() - start

            if not data:
                print(f"[{elapsed:.3f}s] Connection closed")
                break

            responses.extend(data)

            print(f"[{elapsed:.3f}s] Received:")
            print(data.decode("latin-1"))
            print("-" * 50)

        statuses = re.findall(
            rb"HTTP/1\.1 (\d{3})\b",
            bytes(responses)
        )

        statuses = [int(s) for s in statuses]

        print("\nResponse order:", statuses)

        if statuses == [200, 400]:
            print("PASSED: responses were received in the correct order.")
        elif statuses == [400, 200]:
            print("FAILED: responses were received out of order.")
        else:
            print("INCONCLUSIVE: check the received responses and server logs.")


if __name__ == "__main__":
    main()