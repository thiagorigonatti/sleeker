/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http2;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.Http2Headers;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;


public final class Http2Request {

    private final InetSocketAddress localAddress;
    private final InetSocketAddress remoteAddress;
    private final HttpMethod method;
    private final Http2Headers headers;
    private final String path;
    private final Map<String, List<String>> queryParams;
    private final String body;

    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public Http2Headers getHeaders() {
        return headers;
    }

    public String getPath() {
        return path;
    }

    public Map<String, List<String>> getQueryParams() {
        return queryParams;
    }

    public String getBody() {
        return body;
    }

    Http2Request(InetSocketAddress localAddress, InetSocketAddress remoteAddress, HttpMethod method,
                 Http2Headers headers, String path, Map<String, List<String>> queryParams, String body) {
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
        this.method = method;
        this.headers = headers;
        this.path = path;
        this.queryParams = queryParams;
        this.body = body;
    }

}
