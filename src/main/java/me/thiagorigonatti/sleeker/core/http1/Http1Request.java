/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http1;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;

import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;


public final class Http1Request {

    private final SocketAddress localAddress;
    private final SocketAddress remoteAddress;
    private final HttpMethod method;
    private final HttpHeaders headers;
    private final String path;
    private final Map<String, List<String>> queryParams;
    private final byte[] body;

    public SocketAddress getLocalAddress() {
        return localAddress;
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public String getPath() {
        return path;
    }

    public Map<String, List<String>> getQueryParams() {
        return queryParams;
    }

    public byte[] getBody() {
        return body;
    }

    Http1Request(SocketAddress localAddress, SocketAddress remoteAddress, HttpMethod method,
                 HttpHeaders headers, String path, Map<String, List<String>> queryParams, byte[] body) {
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
        this.method = method;
        this.headers = headers;
        this.path = path;
        this.queryParams = queryParams;
        this.body = body;
    }

    public String getBodyAsString() {
        return new String(this.body, StandardCharsets.UTF_8);
    }

    public String getBodyAsString(final Charset charset) {
        return new String(this.body, charset);
    }
}
