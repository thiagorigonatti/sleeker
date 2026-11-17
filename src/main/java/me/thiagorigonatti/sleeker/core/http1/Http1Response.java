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
import me.thiagorigonatti.sleeker.exception.HttpSleekException;
import me.thiagorigonatti.sleeker.util.ContentType;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class Http1Response implements HeaderAddeable {

    private boolean committed;

    public boolean isCommitted() {
        return this.committed;
    }

    public void commit() {
        if (this.committed) throw new IllegalStateException("HTTP response has already been committed");
        this.committed = true;
    }

    private final ChannelHandlerContext ctx;
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

    public boolean replyFile(final Path path, final HttpResponseStatus status) {

        if (this.ctx.pipeline().get(SslHandler.class) != null) {
            throw new RuntimeException("Attempt to serve zero-copy file while using TLS.");
        }

        final FileChannel fileChannel;

        try {
            fileChannel = FileChannel.open(path, StandardOpenOption.READ);

        } catch (final NoSuchFileException exception) {
            throw new HttpSleekException.BaseBuilder<>().responseMessage(HttpResponseStatus.NOT_FOUND.reasonPhrase()).contentType(ContentType.TEXT_PLAIN_UTF8).httpResponseStatus(HttpResponseStatus.NOT_FOUND).build();

        } catch (final IOException exception) {
            throw new HttpSleekException.BaseBuilder<>().responseMessage(HttpResponseStatus.INTERNAL_SERVER_ERROR.reasonPhrase()).contentType(ContentType.TEXT_PLAIN_UTF8).httpResponseStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR).build();
        }

        final DefaultFileRegion region;

        try {
            final long fileSize = fileChannel.size();

            region = new DefaultFileRegion(fileChannel, 0, fileSize);

        } catch (final IOException exception) {

            try {
                fileChannel.close();
            } catch (final IOException ignored) {
            }

            throw new HttpSleekException.BaseBuilder<>()
                    .responseMessage(HttpResponseStatus.INTERNAL_SERVER_ERROR.reasonPhrase())
                    .contentType(ContentType.TEXT_PLAIN_UTF8)
                    .httpResponseStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }

        try {
            commit();
        } catch (final RuntimeException exception) {
            region.release();
            throw exception;
        }

        final DefaultHttpResponse response = new DefaultHttpResponse(this.httpVersion, status);

        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, region.count());

        response.headers().add(this.httpHeaders);

        try {
            this.ctx.write(response);
            this.ctx.write(region);
        } catch (final RuntimeException exception) {
            region.release();
            throw exception;
        }

        this.ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

        return isCommitted();
    }

    public boolean replyFileChunked(final Path path, final HttpResponseStatus status) {

        if (this.ctx.pipeline().get(SslHandler.class) == null) {
            throw new IllegalStateException("Attempt to serve chunked file without TLS.");
        }

        final int chunkSize = Config.getHttp1ChunkSize();

        if (chunkSize <= 0) {
            throw new IllegalStateException("HTTP/1 chunk size must be greater than zero");
        }

        final RandomAccessFile raf;

        try {
            raf = new RandomAccessFile(path.toFile(), "r");

        } catch (final FileNotFoundException exception) {
            throw new HttpSleekException.BaseBuilder<>().responseMessage(HttpResponseStatus.NOT_FOUND.reasonPhrase()).contentType(ContentType.TEXT_PLAIN_UTF8).httpResponseStatus(HttpResponseStatus.NOT_FOUND).build();
        }

        final ChunkedFile chunkedFile;

        try {
            chunkedFile = new ChunkedFile(raf, 0, raf.length(), chunkSize);

        } catch (final IOException | RuntimeException exception) {

            close(raf);

            throw new HttpSleekException.BaseBuilder<>().responseMessage(HttpResponseStatus.INTERNAL_SERVER_ERROR.reasonPhrase()).contentType(ContentType.TEXT_PLAIN_UTF8).httpResponseStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR).build();
        }

        try {
            commit();

        } catch (final RuntimeException exception) {

            close(chunkedFile);

            throw exception;
        }

        final DefaultHttpResponse response = new DefaultHttpResponse(this.httpVersion, status);

        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED).add(this.httpHeaders);

        final HttpChunkedInput chunkedInput = new HttpChunkedInput(chunkedFile);

        try {
            this.ctx.write(response);
            this.ctx.writeAndFlush(chunkedInput);

        } catch (final RuntimeException exception) {

            // The writing wasn't even delivered to the pipeline as normal, so the resource remains our responsibility.
            close(chunkedFile);

            throw exception;
        }


        // From this point on, ChunkedWriteHandler has ChunkedInput.
        // It closes ChunkedInput both when the transfer is complete and when the write fails.
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


    public void addHeader(final CharSequence httpHeaderName, final CharSequence httpHeaderValue) {
        this.httpHeaders.add(httpHeaderName, httpHeaderValue);
    }


    public boolean reply(final String body, final HttpResponseStatus httpResponseStatus) {
        commit();

        final ByteBuf buf = this.ctx.alloc().buffer();
        buf.writeCharSequence(body, CharsetUtil.UTF_8);

        final FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, httpResponseStatus, buf);

        if (httpResponseStatus != HttpResponseStatus.NO_CONTENT)
            fullHttpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());

        fullHttpResponse.headers().add(this.httpHeaders);
        this.ctx.writeAndFlush(fullHttpResponse);

        return isCommitted();
    }
}
