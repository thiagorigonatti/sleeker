/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.*;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;
import me.thiagorigonatti.sleeker.core.Config;
import me.thiagorigonatti.sleeker.core.HeaderAddeable;
import me.thiagorigonatti.sleeker.exception.HttpSleekException;
import me.thiagorigonatti.sleeker.util.ContentType;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

public class Http2Response implements HeaderAddeable {

    private final ChannelHandlerContext ctx;
    private final DefaultHttp2Headers defaultHttp2Headers;
    private final Http2FrameStream http2FrameStream;
    private final HttpMethod httpMethod;

    Http2Response(final ChannelHandlerContext ctx, final Http2FrameStream http2FrameStream, final HttpMethod httpMethod) {
        this.ctx = ctx;
        this.defaultHttp2Headers = new DefaultHttp2Headers();
        this.http2FrameStream = http2FrameStream;
        this.httpMethod = httpMethod;
    }

    private boolean committed;

    public boolean isCommitted() {
        return this.committed;
    }

    public void commit() {
        if (this.committed) throw new IllegalStateException("HTTP response has already been committed");
        this.committed = true;
    }

    public void addHeader(final CharSequence httpHeaderName, final CharSequence httpHeaderValue) {
        this.defaultHttp2Headers.add(httpHeaderName, httpHeaderValue);
    }

    public boolean replyFileChunked(final Path path, final HttpResponseStatus status) {

        final int chunkSize = Config.getHttp2ChunkSize();

        if (chunkSize <= 0) {
            throw new IllegalStateException("HTTP/2 chunk size must be greater than zero");
        }

        final RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(path.toFile(), "r");
        } catch (final FileNotFoundException exception) {
            throw new HttpSleekException.BaseBuilder<>()
                    .responseMessage(HttpResponseStatus.NOT_FOUND.reasonPhrase())
                    .contentType(ContentType.TEXT_PLAIN_UTF8)
                    .httpResponseStatus(HttpResponseStatus.NOT_FOUND).build();
        }

        final long length;

        try {
            length = raf.length();
        } catch (final IOException exception) {
            close(raf);
            throw new HttpSleekException.BaseBuilder<>()
                    .responseMessage(HttpResponseStatus.INTERNAL_SERVER_ERROR.reasonPhrase())
                    .contentType(ContentType.TEXT_PLAIN_UTF8)
                    .httpResponseStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }

        /*
         *HEAD:
         *
         * we don't need to create ChunkedFile or transfer DATA.
         */
        if (this.httpMethod.equals(HttpMethod.HEAD)) {
            try {
                commit();
            } finally {
                close(raf);
            }
            this.defaultHttp2Headers.status(status.codeAsText());
            this.defaultHttp2Headers.setLong(HttpHeaderNames.CONTENT_LENGTH, length);
            this.ctx.writeAndFlush(new DefaultHttp2HeadersFrame(this.defaultHttp2Headers, true).stream(this.http2FrameStream));
            return isCommitted();
        }

        final ChunkedFile chunkedFile;
        try {
            chunkedFile = new ChunkedFile(raf, 0, length, chunkSize);
        } catch (final IOException | RuntimeException exception) {
            close(raf);
            throw new HttpSleekException.BaseBuilder<>()
                    .responseMessage(HttpResponseStatus.INTERNAL_SERVER_ERROR.reasonPhrase())
                    .contentType(ContentType.TEXT_PLAIN_UTF8)
                    .httpResponseStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }

        try {
            commit();
        } catch (final RuntimeException exception) {
            close(chunkedFile);
            throw exception;
        }

        this.defaultHttp2Headers.status(status.codeAsText());
        this.defaultHttp2Headers.setLong(HttpHeaderNames.CONTENT_LENGTH, length);
        final Http2DataChunkedInput chunkedInput = new Http2DataChunkedInput(chunkedFile, this.http2FrameStream);

        try {
            this.ctx.write(new DefaultHttp2HeadersFrame(this.defaultHttp2Headers, false).stream(this.http2FrameStream));
            this.ctx.writeAndFlush(chunkedInput);
        } catch (final RuntimeException exception) {

            /*
             * ChunkedWriteHandler has not yet received normally
             * ChunkedInput, so the responsibility remains ours.
             */
            close(chunkedFile);
            throw exception;
        }
        return isCommitted();
    }


    private static void close(final RandomAccessFile raf) {

        try {
            raf.close();
        } catch (final IOException ignored) {
        }
    }

    private static void close(final ChunkedFile chunkedFile) {

        try {
            chunkedFile.close();
        } catch (final Exception ignored) {
        }
    }

    public void reply(final CharSequence responseContent, final HttpResponseStatus httpResponseStatus) {

        this.defaultHttp2Headers.status(httpResponseStatus.codeAsText());

        final boolean noBody = this.httpMethod.equals(HttpMethod.HEAD)
                || httpResponseStatus.code() >= 100 && httpResponseStatus.code() < 200
                || httpResponseStatus.equals(HttpResponseStatus.NO_CONTENT)
                || httpResponseStatus.equals(HttpResponseStatus.RESET_CONTENT)
                || httpResponseStatus.equals(HttpResponseStatus.NOT_MODIFIED);

        if (noBody) {
            this.ctx.writeAndFlush(new DefaultHttp2HeadersFrame(this.defaultHttp2Headers, true).stream(this.http2FrameStream));
            return;
        }

        final ByteBuf body = this.ctx.alloc().buffer();
        body.writeCharSequence(responseContent, CharsetUtil.UTF_8);
        this.ctx.write(new DefaultHttp2HeadersFrame(this.defaultHttp2Headers, false).stream(this.http2FrameStream));
        this.ctx.writeAndFlush(new DefaultHttp2DataFrame(body, true).stream(this.http2FrameStream));
    }
}
