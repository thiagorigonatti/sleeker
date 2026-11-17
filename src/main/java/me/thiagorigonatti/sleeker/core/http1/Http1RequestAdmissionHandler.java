/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http1;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;

import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;

import java.util.ArrayDeque;

public final class Http1RequestAdmissionHandler extends ChannelDuplexHandler {

    private static final AttributeKey<Http1RequestAdmissionHandler> KEY = AttributeKey.valueOf("sleeker.http1.admission");

    private final Http1RequestAdmissionController controller;

    private final ArrayDeque<Http1RequestAdmissionController.Permit> awaitingRouter = new ArrayDeque<>();

    private Http1RequestAdmissionController.Permit receiving;

    private boolean closing;
    private boolean closeAfterResponse;

    public Http1RequestAdmissionHandler(final Http1RequestAdmissionController controller) {

        this.controller = controller;
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {

        ctx.channel().attr(KEY).set(this);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {

        if (closing) {
            ReferenceCountUtil.release(msg);
            return;
        }

        if (msg instanceof HttpRequest) {

            // Shouldn't start a new request before finishing the body of the previous one.
            if (receiving != null) {
                reject(ctx, msg);
                return;
            }

            final var permit = controller.tryAcquire();

            if (permit == null) {
                reject(ctx, msg);
                return;
            }

            receiving = permit;
            awaitingRouter.addLast(permit);
        }

        if (msg instanceof HttpContent content) {

            final int bytes = content.content().readableBytes();

            if (receiving == null || !receiving.tryReserveBody(bytes)) {

                reject(ctx, msg);
                return;
            }

            if (msg instanceof LastHttpContent) {
                receiving = null;
            }
        }

        ctx.fireChannelRead(msg);
    }

    private void reject(final ChannelHandlerContext ctx, final Object msg) {

        closing = true;

        // The object will not be forwarded to another handler.
        ReferenceCountUtil.release(msg);

        ctx.close();
    }

    public static Http1RequestAdmissionController.Permit take(final ChannelHandlerContext ctx) {

        final var handler = ctx.channel().attr(KEY).get();

        if (handler == null) {
            return null;
        }

        // Called by the router in the channel's Event Loop.
        return handler.awaitingRouter.pollFirst();
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, ChannelPromise promise) throws Exception {

        if (msg instanceof HttpResponse response) {

            final int status = response.status().code();

            // The HttpObjectAggregator may produce a 413/417 error before delivering a FullHttpRequest to the router.
            // In this project, we chose to close after receiving a response.
            if (status == 413 || status == 417) {
                closeAfterResponse = true;
            }
        }

        if (closeAfterResponse && msg instanceof LastHttpContent) {

            closeAfterResponse = false;

            promise = promise.unvoid();
            promise.addListener(ChannelFutureListener.CLOSE);
        }

        ctx.write(msg, promise);
    }

    private void cleanup() {

        closing = true;

        var permit = receiving;
        receiving = null;

        if (permit != null) {
            permit.close();
        }

        while ((permit = awaitingRouter.pollFirst()) != null) {
            permit.close();
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {

        cleanup();
        ctx.fireChannelInactive();
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) throws Exception {

        cleanup();
        ctx.channel().attr(KEY).set(null);

        super.handlerRemoved(ctx);
    }
}
