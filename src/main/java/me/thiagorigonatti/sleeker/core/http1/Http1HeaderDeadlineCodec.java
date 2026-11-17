/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http1;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import me.thiagorigonatti.sleeker.core.Config;

import java.util.ArrayDeque;
import java.util.List;

public final class Http1HeaderDeadlineCodec extends CombinedChannelDuplexHandler<HttpRequestDecoder, HttpResponseEncoder> {

    private final Http1HeaderTimeout timeout;
    private final ArrayDeque<HttpMethod> methods = new ArrayDeque<>();

    private boolean closeAfterResponse;

    public Http1HeaderDeadlineCodec(final Http1HeaderTimeout timeout) {

        this.timeout = timeout;

        init(new Decoder(), new Encoder());
    }

    private final class Decoder extends HttpRequestDecoder {

        private boolean receivingBody;
        private boolean discard;

        private Decoder() {
            super(new HttpDecoderConfig());
        }

        @Override
        protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) throws Exception {

            if (discard) {
                in.skipBytes(in.readableBytes());
                return;
            }

            // We are about to decode a header.
            // This could be the start of another request in the same buffer.
            if (!receivingBody && in.isReadable()) {
                timeout.bytesReceived(ctx);
            }

            final int first = out.size();

            //HTTP parsing remains the responsibility of Netty.
            super.decode(ctx, in, out);

            for (int i = first; i < out.size(); i++) {

                final Object message = out.get(i);

                if (message instanceof HttpRequest request) {

                    if (methods.size() >= Config.getHttp1MaxPipelineDepth()) {
                        closeAfterResponse = true;
                        discard = true;

                        for (int j = first; j < out.size(); j++) {
                            ReferenceCountUtil.release(out.get(j));
                        }

                        out.subList(first, out.size()).clear();

                        throw new IllegalStateException("HTTP/1 maxPipelineDepth exceeded");
                    }

                    methods.addLast(request.method());

                    timeout.headerCompleted();
                    receivingBody = true;
                }

                if (message instanceof LastHttpContent) {
                    timeout.requestCompleted();
                    receivingBody = false;
                }
            }
        }

        @Override
        protected void handleTransferEncodingChunkedWithContentLength(final HttpMessage message) {

            super.handleTransferEncodingChunkedWithContentLength(message);
            closeAfterResponse = true;
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) throws Exception {

            timeout.close();
            super.channelInactive(ctx);
        }

        @Override
        protected void handlerRemoved0(final ChannelHandlerContext ctx) throws Exception {

            timeout.close();
            super.handlerRemoved0(ctx);
        }
    }

    private final class Encoder extends HttpResponseEncoder {

        private HttpMethod responseMethod;

        @Override
        public void write(final ChannelHandlerContext ctx, final Object msg, ChannelPromise promise) throws Exception {

            if (closeAfterResponse && msg instanceof LastHttpContent) {
                closeAfterResponse = false;

                promise = promise.unvoid().addListener(ChannelFutureListener.CLOSE);
            }

            super.write(ctx, msg, promise);
        }

        @Override
        protected boolean isContentAlwaysEmpty(final HttpResponse response) {

            // 1xx does not consume a request from the queue.
            if (response.status().codeClass() == HttpStatusClass.INFORMATIONAL) {

                return super.isContentAlwaysEmpty(response);
            }

            responseMethod = methods.pollFirst();

            // HEAD cannot send the response body.
            return HttpMethod.HEAD.equals(responseMethod) || super.isContentAlwaysEmpty(response);
        }

        @Override
        protected void sanitizeHeadersBeforeEncode(final HttpResponse response, final boolean isAlwaysEmpty) {

            if (!isAlwaysEmpty && HttpMethod.CONNECT.equals(responseMethod) && response.status().codeClass() == HttpStatusClass.SUCCESS) {

                response.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
                return;
            }

            super.sanitizeHeadersBeforeEncode(response, isAlwaysEmpty);
        }
    }
}

