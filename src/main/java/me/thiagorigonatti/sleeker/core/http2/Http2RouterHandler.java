/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import me.thiagorigonatti.sleeker.core.Config;
import me.thiagorigonatti.sleeker.core.HttpRouterRunnable;
import me.thiagorigonatti.sleeker.core.SleekerServer;
import me.thiagorigonatti.sleeker.exception.Http2SleekException;
import me.thiagorigonatti.sleeker.guard.Cors;
import me.thiagorigonatti.sleeker.guard.CorsAdder;
import me.thiagorigonatti.sleeker.util.ContentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

@ChannelHandler.Sharable
public class Http2RouterHandler extends SimpleChannelInboundHandler<Http2Frame> {

    private static final Logger LOGGER = LogManager.getLogger(Http2RouterHandler.class);

    private static final AttributeKey<Map<Http2FrameStream, Http2HeaderHolder>> HTTP2_REQUEST_MAP = AttributeKey.valueOf(Http2RouterHandler.class, "http2RequestMap");

    private Cors cors;
    private boolean corsEnabled;

    public final Map<String, Http2Setup> handlers = new HashMap<>();

    public final Map<Path, Http2Setup> fileHandlers = new HashMap<>();

    public Cors getCors() {
        return this.cors;
    }

    public void setCors(final Cors cors) {
        this.cors = cors;
    }

    public boolean isCorsEnabled() {
        return this.corsEnabled;
    }

    public void setCorsEnabled(final boolean corsEnabled) {
        this.corsEnabled = corsEnabled;
    }

    private Map<Http2FrameStream, Http2HeaderHolder> getRequestMap(final ChannelHandlerContext ctx) {

        final Attribute<Map<Http2FrameStream, Http2HeaderHolder>> attribute = ctx.channel().attr(HTTP2_REQUEST_MAP);

        final Map<Http2FrameStream, Http2HeaderHolder> current = attribute.get();

        if (current != null) {
            return current;
        }

        final Map<Http2FrameStream, Http2HeaderHolder> created = new HashMap<>();

        final Map<Http2FrameStream, Http2HeaderHolder> existing = attribute.setIfAbsent(created);

        return existing == null ? created : existing;
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {

        if (cause instanceof final Http2SleekException http2SleekException) {

            final Http2Headers http2Headers = http2SleekException.getHttp2Headers();

            final Http2FrameStream http2FrameStream = http2SleekException.getHttp2FrameStream();

            final HttpResponseStatus httpResponseStatus = http2SleekException.getHttpResponseStatus();

            final CharSequence responseMessage = http2SleekException.getResponseMessage();

            final ContentType contentType = http2SleekException.getContentType();

            if (ctx.channel().isActive()) {

                final Http2Headers headers = new DefaultHttp2Headers().status(httpResponseStatus.codeAsText()).set(HttpHeaderNames.CONTENT_TYPE, contentType.getMimeType());

                final HttpMethod httpMethod = HttpMethod.valueOf(http2Headers.method().toString());

                final boolean end = httpMethod.equals(HttpMethod.HEAD);

                ctx.write(new DefaultHttp2HeadersFrame(headers, end).stream(http2FrameStream));

                if (!end) {

                    final ByteBuf body = ctx.alloc().buffer();

                    body.writeCharSequence(responseMessage, CharsetUtil.UTF_8);

                    ctx.write(new DefaultHttp2DataFrame(body, true).stream(http2FrameStream));
                }

                ctx.flush();

            } else {

                ctx.close();
            }

        } else {

            ctx.close();

            try {

                throw cause;

            } catch (final Throwable exception) {

                throw new RuntimeException(exception);
            }
        }
    }

    private void toRun(final ChannelHandlerContext ctx, final HttpRouterRunnable handler) {

        try {

            handler.run();

        } catch (final Exception exception) {

            LOGGER.warn(exception.getMessage());

            exceptionCaught(ctx, exception);
        }
    }

    private Runnable getTask(final ChannelHandlerContext ctx, final Http2Setup setup, final Http2Headers headers, final String path, final Map<String, List<String>> queryParams, final String body, final Http2FrameStream stream) {

        final HttpMethod method = HttpMethod.valueOf(headers.method().toString());

        final Http2Response http2Response = new Http2Response(ctx, stream, method);

        if (setup.cors() != null) {

            setup.httpMethodList().add(HttpMethod.OPTIONS);

            CorsAdder.addCors(setup.cors(), http2Response);

        } else if (this.isCorsEnabled()) {

            CorsAdder.addCors(this.getCors(), http2Response);
        }

        if (!setup.httpMethodList().contains(method)) {

            return () -> Http2Responder.reply(ctx, headers, stream, HttpResponseStatus.METHOD_NOT_ALLOWED);
        }

        final Http2Request http2Request = new Http2Request((InetSocketAddress) ctx.channel().localAddress(), (InetSocketAddress) ctx.channel().remoteAddress(), method, headers, path, queryParams, body);

        return switch (headers.method().toString()) {

            case "GET" -> () -> toRun(ctx, () -> setup.http2SleekHandler().handleGET(http2Request, http2Response));

            case "POST" -> () -> toRun(ctx, () -> setup.http2SleekHandler().handlePOST(http2Request, http2Response));

            case "PUT" -> () -> toRun(ctx, () -> setup.http2SleekHandler().handlePUT(http2Request, http2Response));

            case "PATCH" -> () -> toRun(ctx, () -> setup.http2SleekHandler().handlePATCH(http2Request, http2Response));

            case "DELETE" ->
                    () -> toRun(ctx, () -> setup.http2SleekHandler().handleDELETE(http2Request, http2Response));

            case "HEAD" -> () -> toRun(ctx, () -> setup.http2SleekHandler().handleHEAD(http2Request, http2Response));

            case "OPTIONS" -> () -> toRun(ctx, () -> {

                if (setup.cors() != null || this.isCorsEnabled()) {

                    http2Response.reply(HttpResponseStatus.NO_CONTENT.reasonPhrase(), HttpResponseStatus.NO_CONTENT);

                } else {

                    setup.http2SleekHandler().handleOPTIONS(http2Request, http2Response);
                }
            });

            case "TRACE" -> () -> toRun(ctx, () -> setup.http2SleekHandler().handleTRACE(http2Request, http2Response));

            case "CONNECT" ->
                    () -> toRun(ctx, () -> setup.http2SleekHandler().handleCONNECT(http2Request, http2Response));

            default -> () -> Http2Responder.reply(ctx, headers, stream, HttpResponseStatus.METHOD_NOT_ALLOWED);
        };
    }


    private void rejectRequest(final ChannelHandlerContext ctx, final Http2Headers headers, final Http2FrameStream stream, final HttpResponseStatus status, final boolean requestEnded) {

        Http2Responder.reply(ctx, headers, stream, status);

        if (!requestEnded) {

            ctx.write(new DefaultHttp2ResetFrame(Http2Error.NO_ERROR).stream(stream));
        }
    }

    private void execute(final ChannelHandlerContext ctx, final Http2Headers headers, final Http2FrameStream stream, final Runnable task) {

        try {

            Config.EXECUTOR_SERVICE.execute(task);

        } catch (final RejectedExecutionException exception) {

            Http2Responder.reply(ctx, headers, stream, HttpResponseStatus.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final Http2Frame msg) {

        final Map<Http2FrameStream, Http2HeaderHolder> http2RequestMap = getRequestMap(ctx);

        /*
         * ------------------------------------------------------------
         * HEADERS
         * ------------------------------------------------------------
         */
        if (msg instanceof final Http2HeadersFrame headersFrame) {

            final Http2FrameStream stream = headersFrame.stream();

            /*
             * If there is already context for this stream,
             * this HEADERS is a trailer.
             */
            final Http2HeaderHolder existingContext = http2RequestMap.get(stream);

            if (existingContext != null) {

                /*
                 * Trailers must end the stream.
                 */
                if (!headersFrame.isEndStream()) {

                    final Http2HeaderHolder context = http2RequestMap.remove(stream);

                    if (context != null) {
                        context.body().release();
                    }

                    ctx.write(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR).stream(stream));

                    return;
                }

                /*
                 * Trailers cannot contain pseudo-headers.
                 */
                final Http2Headers trailers = headersFrame.headers();

                if (trailers.method() != null || trailers.scheme() != null || trailers.authority() != null || trailers.path() != null || trailers.status() != null) {

                    final Http2HeaderHolder context = http2RequestMap.remove(stream);

                    if (context != null) {
                        context.body().release();
                    }

                    ctx.write(new DefaultHttp2ResetFrame(Http2Error.PROTOCOL_ERROR).stream(stream));

                    return;
                }

                /*
                 * Valid trailer + END_STREAM.
                 */
                final Http2HeaderHolder context = http2RequestMap.remove(stream);

                if (context == null) {
                    return;
                }

                final String body;

                try {

                    body = context.body().toString(CharsetUtil.UTF_8);

                } finally {

                    context.body().release();
                }

                final Runnable task = getTask(ctx, context.http2Setup(), context.headers(), context.path(), context.queryParams(), body, stream);

                execute(ctx, context.headers(), stream, task);

                return;
            }

            /*
             * Initial HEADERS.
             */
            final Http2Headers headers = headersFrame.headers();

            /*
             * Request inicial precisa de :method.
             */
            if (headers.method() == null) {

                rejectRequest(ctx, headers, stream, HttpResponseStatus.BAD_REQUEST, headersFrame.isEndStream());

                return;
            }

            /*
             * Your current routing depends on :path.
             */
            if (headers.path() == null) {

                rejectRequest(ctx, headers, stream, HttpResponseStatus.BAD_REQUEST, headersFrame.isEndStream());

                return;
            }

            /*
             * We decode :path only ONCE.
             *
             * Example:
             *
             * /users?id=10
             *
             *path:
             * /users
             *
             * queryParams:
             *id -> ["10"]
             */
            final QueryStringDecoder decoder;

            try {

                decoder = new QueryStringDecoder(headers.path().toString());

            } catch (final IllegalArgumentException exception) {

                rejectRequest(ctx, headers, stream, HttpResponseStatus.BAD_REQUEST, headersFrame.isEndStream());

                return;
            }

            final String path = decoder.path();

            final Map<String, List<String>> queryParams = decoder.parameters();

            /*
             * --------------------------------------------------------
             *Normal handler
             * --------------------------------------------------------
             */
            Http2Setup http2Setup = this.handlers.get(path);

            if (http2Setup == null) {

                final Path requestedPath;

                try {

                    requestedPath = Path.of(path).toAbsolutePath().normalize();

                } catch (final InvalidPathException exception) {

                    rejectRequest(ctx, headers, stream, HttpResponseStatus.BAD_REQUEST, headersFrame.isEndStream());

                    return;
                }

                /*
                 * ----------------------------------------------------
                 * File handler
                 * ----------------------------------------------------
                 */
                for (final Map.Entry<Path, Http2Setup> entry : this.fileHandlers.entrySet()) {

                    final Path rootPath = entry.getKey();

                    if (requestedPath.startsWith(rootPath)) {

                        http2Setup = entry.getValue();

                        break;
                    }
                }
            }

            /*
             * --------------------------------------------------------
             * 404
             * --------------------------------------------------------
             */
            if (http2Setup == null) {

                rejectRequest(ctx, headers, stream, HttpResponseStatus.NOT_FOUND, headersFrame.isEndStream());

                return;
            }

            /*
             * --------------------------------------------------------
             * Request without body.
             *
             * HEADERS + END_STREAM
             * --------------------------------------------------------
             */
            if (headersFrame.isEndStream()) {

                final Runnable task = getTask(ctx, http2Setup, headers, path, queryParams, "", stream);

                execute(ctx, headers, stream, task);

                return;
            }

            /*
             * --------------------------------------------------------
             * Request will continue with DATA/trailers.
             *
             * We save path/queryParams now, because the decoder
             * will not exist when the DATA frames arrive.
             * --------------------------------------------------------
             */
            final ByteBuf body = ctx.alloc().buffer();

            http2RequestMap.put(stream, new Http2HeaderHolder(headers, http2Setup, body, path, queryParams));

            return;
        }

        /*
         * ------------------------------------------------------------
         * DATA
         * ------------------------------------------------------------
         */
        if (msg instanceof final Http2DataFrame dataFrame) {

            final Http2FrameStream stream = dataFrame.stream();

            final Http2HeaderHolder context = http2RequestMap.get(stream);

            if (context == null) {
                return;
            }

            final int readableBytes = dataFrame.content().readableBytes();

            /*
             * Maximum request body limit.
             */
            if ((long) context.body().readableBytes() + readableBytes > 2265536) {

                http2RequestMap.remove(stream);

                context.body().release();

                rejectRequest(ctx, context.headers(), stream, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, dataFrame.isEndStream());

                return;
            }

            /*
             * Http2DataFrame will be auto-released by
             * SimpleChannelInboundHandler.
             *
             * Therefore we copy the bytes to our own buffer.
             */
            context.body().writeBytes(dataFrame.content(), dataFrame.content().readerIndex(), readableBytes);

            /*
             * Ainda virão outros DATA frames.
             */
            if (!dataFrame.isEndStream()) {
                return;
            }

            /*
             * --------------------------------------------------------
             * Complete request by DATA + END_STREAM.
             * --------------------------------------------------------
             */
            final Http2HeaderHolder completed = http2RequestMap.remove(stream);

            if (completed == null) {
                return;
            }

            final String body;

            try {

                body = completed.body().toString(CharsetUtil.UTF_8);

            } finally {

                completed.body().release();
            }

            final Runnable task = getTask(ctx, completed.http2Setup(), completed.headers(), completed.path(), completed.queryParams(), body, stream);

            execute(ctx, completed.headers(), stream, task);

            return;
        }

        /*
         * ------------------------------------------------------------
         * RST_STREAM
         * ------------------------------------------------------------
         */
        if (msg instanceof final Http2ResetFrame resetFrame) {

            final Http2HeaderHolder context = http2RequestMap.remove(resetFrame.stream());

            if (context != null) {
                context.body().release();
            }
        }
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) throws Exception {

        ctx.flush();

        super.channelReadComplete(ctx);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {

        final Map<Http2FrameStream, Http2HeaderHolder> requests = ctx.channel().attr(HTTP2_REQUEST_MAP).getAndSet(null);

        if (requests != null) {

            for (final Http2HeaderHolder context : requests.values()) {

                context.body().release();
            }

            requests.clear();
        }

        super.channelInactive(ctx);
    }
}