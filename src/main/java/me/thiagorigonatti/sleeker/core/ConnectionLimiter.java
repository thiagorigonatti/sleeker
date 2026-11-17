/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.concurrent.atomic.AtomicInteger;

@ChannelHandler.Sharable
public final class ConnectionLimiter extends ChannelInboundHandlerAdapter {

    private final int maxConnections;
    private final AtomicInteger active = new AtomicInteger();

    public ConnectionLimiter(int maxConnections) {
        if (maxConnections <= 0) {
            throw new IllegalArgumentException();
        }

        this.maxConnections = maxConnections;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

        int current = active.incrementAndGet();

        if (current > maxConnections) {
            active.decrementAndGet();
            ctx.close();
            return;
        }

        // The vacancy is released exactly when the connection is closed.
        ctx.channel().closeFuture().addListener(ignored -> active.decrementAndGet());

        ctx.fireChannelActive();
    }

    public int activeConnections() {
        return active.get();
    }
}
