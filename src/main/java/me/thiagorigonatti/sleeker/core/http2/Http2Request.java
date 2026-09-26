/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http2;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2Headers;

import java.net.InetSocketAddress;

public record Http2Request(InetSocketAddress localAddress, InetSocketAddress remoteAddress, Http2Headers headers, String body, Http2FrameStream stream, String path, HttpMethod method) {
}
