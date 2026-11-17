/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http2;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.TimeUnit;

public final class Http2RequestTimeoutHandler extends ChannelInboundHandlerAdapter {

    private final long timeout;
    private final TimeUnit timeUnit;

    private ScheduledFuture<?> timeoutFuture;

    private Http2FrameStream stream;

    private boolean initialHeadersReceived;

    public Http2RequestTimeoutHandler(final long timeout, final TimeUnit timeUnit) {

        if (timeout <= 0) {
            throw new IllegalArgumentException("HTTP/2 request timeout must be greater than zero");
        }

        this.timeout = timeout;
        this.timeUnit = timeUnit;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {

        /*
         * First HEADERS of this Http2StreamChannel.
         *
         * As we use Http2MultiplexHandler, this Channel represents
         * exactly one HTTP/2 stream.
         */
        if (msg instanceof final Http2HeadersFrame headersFrame) {

            if (!this.initialHeadersReceived) {

                this.initialHeadersReceived = true;
                this.stream = headersFrame.stream();

                /*
                 * HEADERS + END_STREAM:
                 *
                 * request has already arrived in full, so there is nothing
                 * to wait.
                 */
                if (!headersFrame.isEndStream()) {
                    startTimeout(ctx);
                }

            } else if (headersFrame.isEndStream()) {

                /*
                 * Second HEADERS with END_STREAM:
                 * trailers ending the request.
                 */
                cancelTimeout();
            }

        } else if (msg instanceof final Http2DataFrame dataFrame) {

            /*
             * DATA + END_STREAM:
             * request completely received.
             */
            if (dataFrame.isEndStream()) {
                cancelTimeout();
            }

        } else if (msg instanceof Http2ResetFrame) {

            /*
             * Peer canceled/reset the stream.
             */
            cancelTimeout();
        }

        ctx.fireChannelRead(msg);
    }

    private void startTimeout(final ChannelHandlerContext ctx) {

        cancelTimeout();

        this.timeoutFuture = ctx.executor().schedule(() -> timeout(ctx), this.timeout, this.timeUnit);
    }

    private void cancelTimeout() {

        final ScheduledFuture<?> timeoutFuture = this.timeoutFuture;

        if (timeoutFuture == null) {
            return;
        }

        this.timeoutFuture = null;

        timeoutFuture.cancel(false);
    }

    private void timeout(final ChannelHandlerContext ctx) {

        this.timeoutFuture = null;

        if (!ctx.channel().isActive()) {
            return;
        }

        final Http2FrameStream stream = this.stream;

        if (stream == null) {
            return;
        }

        /*
         * Full HTTP 408 response.
         */
        ctx.write(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().status(HttpResponseStatus.REQUEST_TIMEOUT.codeAsText()), true).stream(stream));

        /*
         * The remote side could still continue sending the request
         * body, then we terminate the stream explicitly.
         *
         * The response has already been completely produced, so
         * NO_ERROR is appropriate to say that we no longer need
         * from the rest of the request.
         */
        ctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.NO_ERROR).stream(stream)).addListener((ChannelFutureListener) future -> ctx.close());
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {

        cancelTimeout();

        super.channelInactive(ctx);
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) throws Exception {

        cancelTimeout();

        super.handlerRemoved(ctx);
    }
}