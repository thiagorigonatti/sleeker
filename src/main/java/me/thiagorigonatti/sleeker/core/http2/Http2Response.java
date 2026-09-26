/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.util.CharsetUtil;
import jakarta.validation.constraints.NotNull;
import me.thiagorigonatti.sleeker.core.HeaderAddeable;

public class Http2Response implements HeaderAddeable {

    private final ChannelHandlerContext ctx;
    private final ByteBuf buf;
    private final DefaultHttp2Headers defaultHttp2Headers;
    private final Http2FrameStream http2FrameStream;
    private final HttpMethod httpMethod;

    public Http2Response(@NotNull ChannelHandlerContext ctx, @NotNull Http2FrameStream http2FrameStream,
                         @NotNull HttpMethod httpMethod) {
        this.ctx = ctx;
        this.buf = ctx.alloc().buffer();
        this.defaultHttp2Headers = new DefaultHttp2Headers();
        this.http2FrameStream = http2FrameStream;
        this.httpMethod = httpMethod;
    }

    public void setBody(@NotNull String body) {
        this.buf.writeCharSequence(body, CharsetUtil.UTF_8);
    }

    public void addHeader(@NotNull CharSequence httpHeaderName, @NotNull CharSequence httpHeaderValue) {
        this.defaultHttp2Headers.add(httpHeaderName, httpHeaderValue);
    }

    public void reply(@NotNull HttpResponseStatus httpResponseStatus) {

        boolean end = httpMethod.equals(HttpMethod.HEAD);

        this.defaultHttp2Headers.status(httpResponseStatus.codeAsText());
        ctx.write(new DefaultHttp2HeadersFrame(defaultHttp2Headers, end).stream(http2FrameStream));

        if (!end) {
            ctx.write(new DefaultHttp2DataFrame(this.buf, true).stream(http2FrameStream));
        }
        ctx.flush();
    }
}
