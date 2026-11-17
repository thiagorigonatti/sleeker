/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http1;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;

import java.util.List;

public final class Http1OrderedObjectAggregator extends HttpObjectAggregator {

    private boolean closing;

    public Http1OrderedObjectAggregator(final int maxContentLength) {

        super(maxContentLength);
    }

    @Override
    public boolean acceptInboundMessage(final Object msg) throws Exception {

        // While the connection closes, discard any HTTP fragments that are still being received.
        if (closing && msg instanceof HttpObject) {
            return true;
        }

        return super.acceptInboundMessage(msg);
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx, final HttpObject msg, final List<Object> out) throws Exception {

        if (closing) {
            return;
        }

        if (msg instanceof HttpRequest request && hasPendingResponses(ctx)) {

            final boolean oversized = isContentLengthInvalid(request, maxContentLength());

            final boolean expectation = request.headers().contains(HttpHeaderNames.EXPECT);

            // The previous request has not yet been answered. We do not allow the aggregator to generate
            // Automatically 413, 417, or 100. Continue to the next request.
            if (oversized || expectation) {
                close(ctx);
                return;
            }
        }

        super.decode(ctx, msg, out);
    }

    @Override
    protected void handleOversizedMessage(final ChannelHandlerContext ctx, final HttpMessage oversized) throws Exception {

        // Covers bodies whose size only exceeds the limit during the reception of chunks.
        if (oversized instanceof HttpRequest && hasPendingResponses(ctx)) {

            close(ctx);
            return;
        }

        super.handleOversizedMessage(ctx, oversized);
    }

    private boolean hasPendingResponses(final ChannelHandlerContext ctx) {

        final Http1ConnectionIdleHandler handler = ctx.pipeline().get(Http1ConnectionIdleHandler.class);

        // In the absence of the counter, it fails conservatively, without generating an out-of-order response.
        return handler == null || handler.hasPendingResponses();
    }

    private void close(final ChannelHandlerContext ctx) {

        closing = true;
        ctx.channel().close();
    }
}
