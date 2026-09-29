/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.*;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;
import me.thiagorigonatti.sleeker.core.Config;
import me.thiagorigonatti.sleeker.core.HeaderAddeable;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

public class Http2Response implements HeaderAddeable {

    private final ChannelHandlerContext ctx;
    private ByteBuf buf;
    private final DefaultHttp2Headers defaultHttp2Headers;
    private final Http2FrameStream http2FrameStream;
    private final HttpMethod httpMethod;

    Http2Response(final ChannelHandlerContext ctx, final Http2FrameStream http2FrameStream,
                  final HttpMethod httpMethod) {
        this.ctx = ctx;
        this.defaultHttp2Headers = new DefaultHttp2Headers();
        this.http2FrameStream = http2FrameStream;
        this.httpMethod = httpMethod;
    }

    public void setBody(final String body) {
        if (this.buf == null) this.buf = this.ctx.alloc().buffer();
        this.buf.writeCharSequence(body, CharsetUtil.UTF_8);
    }

    public void addHeader(final CharSequence httpHeaderName, final CharSequence httpHeaderValue) {
        this.defaultHttp2Headers.add(httpHeaderName, httpHeaderValue);
    }

    public void replyFileChunked(final Path path, final long position, final long count,
                                 final HttpResponseStatus httpResponseStatus) throws IOException {

        final RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
        final ChunkedFile chunkedFile = new ChunkedFile(raf, position, count, Config.getHttp2ChunkSize());

        final boolean end = this.httpMethod.equals(HttpMethod.HEAD);

        this.defaultHttp2Headers.status(httpResponseStatus.codeAsText());
        this.ctx.write(new DefaultHttp2HeadersFrame(this.defaultHttp2Headers, end).stream(this.http2FrameStream));

        if (!end) {
            this.ctx.write(new Http2DataChunkedInput(chunkedFile, this.http2FrameStream));
        }
        this.ctx.flush();
    }

    public void reply(final HttpResponseStatus httpResponseStatus) {

        final boolean end = this.httpMethod.equals(HttpMethod.HEAD);

        this.defaultHttp2Headers.status(httpResponseStatus.codeAsText());
        this.ctx.write(new DefaultHttp2HeadersFrame(this.defaultHttp2Headers, end).stream(this.http2FrameStream));

        if (!end) {
            this.ctx.write(new DefaultHttp2DataFrame(this.buf, true).stream(this.http2FrameStream));
        }
        this.ctx.flush();
    }
}
