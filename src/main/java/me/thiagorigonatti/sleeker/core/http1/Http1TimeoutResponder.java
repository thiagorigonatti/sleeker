/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http1;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;

public final class Http1TimeoutResponder {

    private Http1TimeoutResponder() {
    }

    public static void timeout(final ChannelHandlerContext ctx, final HttpVersion version) {

        if (!ctx.channel().isActive()) {
            return;
        }

        final Http1ConnectionIdleHandler idleHandler = ctx.pipeline().get(Http1ConnectionIdleHandler.class);

        // If there is a previous response pending, we cannot send 408 immediately.
        // We close the connection to prevent out-of-order HTTP/1 responses.
        if (idleHandler == null || idleHandler.hasPendingResponses()) {

            ctx.channel().close();
            return;
        }


        // Do not accept new readings while sending the timeout response.
        ctx.channel().config().setAutoRead(false);

        final FullHttpResponse response = new DefaultFullHttpResponse(version, HttpResponseStatus.REQUEST_TIMEOUT, Unpooled.EMPTY_BUFFER);

        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0).set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);


        // We write for Channel to go through the entire outbound pipeline, including codec HTTP
        // and TLS, when enabled.
        ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}

