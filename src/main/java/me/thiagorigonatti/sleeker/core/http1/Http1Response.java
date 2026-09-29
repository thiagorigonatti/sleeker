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
import me.thiagorigonatti.sleeker.core.Config;
import me.thiagorigonatti.sleeker.core.HeaderAddeable;

import java.io.RandomAccessFile;
import java.nio.file.Path;

public class Http1Response implements HeaderAddeable {

    private final ChannelHandlerContext ctx;
    private ByteBuf buf;
    private final HttpVersion httpVersion;
    private final HttpHeaders httpHeaders;

    ChannelHandlerContext getCtx() {
        return this.ctx;
    }

    Http1Response(final ChannelHandlerContext ctx, final HttpVersion httpVersion) {
        this.ctx = ctx;
        this.httpVersion = httpVersion;
        this.httpHeaders = new DefaultHttpHeaders();
    }

    public void replyFile(final Path path, final long position, final long count, final HttpResponseStatus status) {

        if (this.ctx.pipeline().get(SslHandler.class) != null)
            throw new RuntimeException("Attempt to serve zero-copy file while using TLS.");

        final DefaultHttpResponse defaultHttpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
        defaultHttpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, count);
        defaultHttpResponse.headers().add(this.httpHeaders);
        final DefaultFileRegion region = new DefaultFileRegion(path.toFile(), position, count);

        this.ctx.write(defaultHttpResponse);
        this.ctx.write(region);
        this.ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
    }

    public void replyFileChunked(final Path path, final long position, final long count, final HttpResponseStatus status) throws Exception {

        if (this.ctx.pipeline().get(SslHandler.class) == null)
            throw new RuntimeException("Attempt to serve chunked file without TLS.");

        final DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        response.headers().add(this.httpHeaders);
        final RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
        final ChunkedFile chunkedFile = new ChunkedFile(raf, position, count, Config.getHttp1ChunkSize());
        final HttpChunkedInput chunkedInput = new HttpChunkedInput(chunkedFile);

        this.ctx.write(response);
        this.ctx.writeAndFlush(chunkedInput);
    }

    public void setBody(final String body) {
        if (this.buf == null) this.buf = this.ctx.alloc().buffer();
        this.buf.writeCharSequence(body, CharsetUtil.UTF_8);
    }

    public void addHeader(final CharSequence httpHeaderName, final CharSequence httpHeaderValue) {
        this.httpHeaders.add(httpHeaderName, httpHeaderValue);
    }

    public void reply(final HttpResponseStatus httpResponseStatus) {

        if (this.buf == null) {
            this.buf = this.ctx.alloc().buffer(0);
            this.buf.writeCharSequence("", CharsetUtil.UTF_8);
        }

        final FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(this.httpVersion, httpResponseStatus, this.buf);
        fullHttpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, this.buf.readableBytes());
        fullHttpResponse.headers().add(this.httpHeaders);
        this.ctx.writeAndFlush(fullHttpResponse);
    }
}
