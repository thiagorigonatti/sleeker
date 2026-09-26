/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http1;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;
import jakarta.validation.constraints.NotNull;
import me.thiagorigonatti.sleeker.core.HeaderAddeable;

import java.io.RandomAccessFile;
import java.nio.file.Path;

public class Http1Response implements HeaderAddeable {

    private final ChannelHandlerContext ctx;
    private ByteBuf buf;
    private final HttpVersion httpVersion;
    private final HttpHeaders httpHeaders;

    public ChannelHandlerContext getCtx() {
        return ctx;
    }

    public HttpHeaders getHttpHeaders() {
        return httpHeaders;
    }

    public Http1Response(@NotNull ChannelHandlerContext ctx, @NotNull HttpVersion httpVersion) {
        this.ctx = ctx;
        this.httpVersion = httpVersion;
        this.httpHeaders = new DefaultHttpHeaders();
    }

    public void replyFile(Path path, long position, long count, HttpResponseStatus status) {

        if (ctx.pipeline().get(SslHandler.class) != null)
            throw new RuntimeException("Attempt to serve zero-copy file while using TLS.");

        final DefaultHttpResponse defaultHttpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
        defaultHttpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, count);
        defaultHttpResponse.headers().add(this.httpHeaders);
        DefaultFileRegion region = new DefaultFileRegion(path.toFile(), position, count);

        ctx.write(defaultHttpResponse);
        ctx.write(region);
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
    }

    public void replyFileChunked(Path path, long position, long count, HttpResponseStatus status) throws Exception {

        if (ctx.pipeline().get(SslHandler.class) == null)
            throw new RuntimeException("Attempt to serve chunked file without TLS.");

        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        response.headers().add(this.httpHeaders);
        RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
        ChunkedFile chunkedFile = new ChunkedFile(raf, position, count, 8192);
        HttpChunkedInput chunkedInput = new HttpChunkedInput(chunkedFile);

        ctx.write(response);
        ctx.writeAndFlush(chunkedInput);
    }

    public void setBody(@NotNull String body) {
        if (buf == null) buf = ctx.alloc().buffer();
        buf.writeCharSequence(body, CharsetUtil.UTF_8);
    }

    public void addHeader(@NotNull CharSequence httpHeaderName, @NotNull CharSequence httpHeaderValue) {
        this.httpHeaders.add(httpHeaderName, httpHeaderValue);
    }

    public void reply(@NotNull HttpResponseStatus httpResponseStatus) {
        final FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(this.httpVersion, httpResponseStatus, this.buf);
        fullHttpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, this.buf.readableBytes());
        fullHttpResponse.headers().add(this.httpHeaders);
        ctx.writeAndFlush(fullHttpResponse);
    }
}
