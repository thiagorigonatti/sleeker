/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http1;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;

import java.net.SocketAddress;
import java.util.List;
import java.util.Map;

public record Http1Request(SocketAddress localAddress, SocketAddress remoteAddress, HttpMethod method,
                           HttpHeaders headers, String path, Map<String, List<String>> queryParams, String body) {
}
