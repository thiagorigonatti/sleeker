/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http1;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;

import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

public final class Http1ConnectionIdleHandler extends ChannelDuplexHandler {

    private int pendingResponses;
    private boolean informationalResponse;

    public boolean hasPendingResponses() {
        return pendingResponses > 0;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {

        if (msg instanceof FullHttpRequest) {

            pendingResponses++;

            if (pendingResponses == 1) {

                final IdleStateHandler idle =
                        ctx.pipeline().get(IdleStateHandler.class);

                if (idle == null) {
                    throw new IllegalStateException(
                            "HTTP/1 IdleStateHandler missing"
                    );
                }

                idle.resetWriteTimeout();
            }
        }

        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, ChannelPromise promise) throws Exception {

        if (msg instanceof HttpResponse response) {
            final int code = response.status().code();

            // 1xx (except 101) does not terminate a request.
            informationalResponse = code >= 100 && code < 200 && code != 101;
        }

        if (msg instanceof LastHttpContent) {

            if (!informationalResponse && pendingResponses > 0) {

                promise = promise.unvoid();

                promise.addListener(future -> {

                    if (!future.isSuccess()) {
                        ctx.close();
                        return;
                    }

                    pendingResponses--;
                });
            }

            informationalResponse = false;
        }

        ctx.write(msg, promise);
    }

    @Override
    public void userEventTriggered(
            final ChannelHandlerContext ctx,
            final Object evt) throws Exception {

        if (evt instanceof IdleStateEvent event) {

            if (event.state() == IdleState.WRITER_IDLE) {

                if (pendingResponses > 0) {
                    ctx.close();
                }

                return;
            }

            if (event.state() == IdleState.ALL_IDLE) {

                if (pendingResponses == 0) {
                    ctx.close();
                }

                return;
            }
        }

        ctx.fireUserEventTriggered(evt);
    }
}
