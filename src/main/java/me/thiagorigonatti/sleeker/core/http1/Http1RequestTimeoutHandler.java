/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http1;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.TimeUnit;

public final class Http1RequestTimeoutHandler extends ChannelInboundHandlerAdapter {

    private final long timeout;
    private final TimeUnit timeUnit;

    private ScheduledFuture<?> timeoutFuture;
    private HttpVersion httpVersion;

    public Http1RequestTimeoutHandler(final long timeout, final TimeUnit timeUnit) {

        if (timeout <= 0) {
            throw new IllegalArgumentException("HTTP/1 request timeout must be greater than zero");
        }

        this.timeout = timeout;
        this.timeUnit = timeUnit;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {


        // The HttpServerCodec has finished decoding the headers.
        // From this point on, there is an absolute limit to the arrival of the remainder of the request.
        if (msg instanceof final HttpRequest request) {

            this.httpVersion = request.protocolVersion();

            startTimeout(ctx);
        }


        // The request arrived completely.
        // Cancel the timeout before forwarding the message to the next handlers.
        if (msg instanceof LastHttpContent) {
            cancelTimeout();
        }

        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {

        cancelTimeout();

        ctx.fireChannelInactive();
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) throws Exception {

        cancelTimeout();

        super.handlerRemoved(ctx);
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

        final HttpVersion version = this.httpVersion != null ? this.httpVersion : HttpVersion.HTTP_1_1;

        Http1TimeoutResponder.timeout(ctx, version);
    }
}