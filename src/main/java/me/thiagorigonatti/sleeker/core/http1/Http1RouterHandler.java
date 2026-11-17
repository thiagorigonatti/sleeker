/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http1;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import me.thiagorigonatti.sleeker.core.Config;
import me.thiagorigonatti.sleeker.core.HttpRouterRunnable;
import me.thiagorigonatti.sleeker.exception.HttpSleekException;
import me.thiagorigonatti.sleeker.guard.Cors;
import me.thiagorigonatti.sleeker.guard.CorsAdder;
import me.thiagorigonatti.sleeker.util.ContentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

@ChannelHandler.Sharable
public class Http1RouterHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger LOGGER = LogManager.getLogger(Http1RouterHandler.class);
    private static final AttributeKey<Http1SerialExecutor> SERIAL_EXECUTOR = AttributeKey.valueOf("sleeker.http1.serialExecutor");

    private Cors cors;
    private boolean corsEnabled;

    public final Map<String, Http1Setup> handlers = new HashMap<>();

    public final Map<Path, Http1Setup> fileHandlers = new HashMap<>();

    public Cors getCors() {
        return cors;
    }

    public void setCors(final Cors cors) {
        this.cors = cors;
    }

    public boolean isCorsEnabled() {
        return corsEnabled;
    }

    public void setCorsEnabled(final boolean corsEnabled) {
        this.corsEnabled = corsEnabled;
    }

    private Http1BoundedExecutor http1BoundedExecutor;

    public void setHttp1BoundedExecutor(
            final Http1BoundedExecutor http1BoundedExecutor) {

        this.http1BoundedExecutor =
                Objects.requireNonNull(http1BoundedExecutor);
    }

    private Http1SerialExecutor getSerialExecutor(
            final ChannelHandlerContext ctx) {

        final Attribute<Http1SerialExecutor> attribute =
                ctx.channel().attr(SERIAL_EXECUTOR);

        final Http1SerialExecutor current = attribute.get();

        if (current != null) {
            return current;
        }

        final Http1SerialExecutor created =
                new Http1SerialExecutor(
                        Objects.requireNonNull(
                                this.http1BoundedExecutor,
                                "HTTP/1 bounded executor not initialized"
                        ),
                        ctx.channel()
                );

        final Http1SerialExecutor previous =
                attribute.setIfAbsent(created);

        return previous != null ? previous : created;
    }

    private void execute(final ChannelHandlerContext ctx, final Runnable task, final Http1RequestAdmissionController.Permit permit) {

        try {
            getSerialExecutor(ctx).execute(() -> {
                try {
                    task.run();

                } finally {
                    permit.close();
                }
            }, permit::close);

        } catch (final RejectedExecutionException exception) {

            permit.close();
            ctx.close();

        } catch (final RuntimeException exception) {

            permit.close();

            LOGGER.error("Failed to enqueue HTTP/1 task", exception);

            ctx.close();
        }
    }

    private void enqueueError(final ChannelHandlerContext ctx, final HttpVersion version, final HttpMethod method,
                              final HttpResponseStatus status, final String message, final Http1RequestAdmissionController.Permit permit) {

        execute(ctx, () -> {
            final Http1Response response = new Http1Response(ctx, version);
            response.addHeader(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType());
            response.reply(HttpMethod.HEAD.equals(method) ? "" : message, status);
        }, permit);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {

        LOGGER.error("Unhandled HTTP/1 pipeline exception", cause);

        ctx.close();
    }

    private Runnable getTask(ChannelHandlerContext ctx, Http1Setup setup, FullHttpRequest msg, QueryStringDecoder decoder) {

        final HttpMethod method = msg.method();
        final Http1Response http1Response = new Http1Response(ctx, msg.protocolVersion());

        if (setup.cors() != null) {
            setup.httpMethodList().add(HttpMethod.OPTIONS);
            CorsAdder.addCors(setup.cors(), http1Response);

        } else if (this.isCorsEnabled()) {
            CorsAdder.addCors(this.getCors(), http1Response);
        }

        if (!setup.httpMethodList().contains(msg.method())) {

            return () -> Http1Responder.replyMethodNotAllowed(method, setup.httpMethodList(), http1Response);
        }

        final ByteBuf byteBuf = msg.content();
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);

        final Http1Request http1Request = new Http1Request(ctx.channel().localAddress(), ctx.channel().remoteAddress(), msg.method(), msg.headers().copy(), decoder.path(), decoder.parameters(), bytes);

        return switch (http1Request.getMethod().name()) {

            case "GET" ->
                    () -> toRun(http1Response, () -> setup.http1SleekHandler().handleGET(http1Request, http1Response));

            case "POST" ->
                    () -> toRun(http1Response, () -> setup.http1SleekHandler().handlePOST(http1Request, http1Response));

            case "PUT" ->
                    () -> toRun(http1Response, () -> setup.http1SleekHandler().handlePUT(http1Request, http1Response));

            case "PATCH" ->
                    () -> toRun(http1Response, () -> setup.http1SleekHandler().handlePATCH(http1Request, http1Response));

            case "DELETE" ->
                    () -> toRun(http1Response, () -> setup.http1SleekHandler().handleDELETE(http1Request, http1Response));

            case "HEAD" ->
                    () -> toRun(http1Response, () -> setup.http1SleekHandler().handleHEAD(http1Request, http1Response));

            case "OPTIONS" -> () -> toRun(http1Response, () -> {
                if (setup.cors() != null || this.isCorsEnabled()) {
                    http1Response.reply("", HttpResponseStatus.NO_CONTENT);
                } else {
                    setup.http1SleekHandler().handleOPTIONS(http1Request, http1Response);
                }
            });

            case "TRACE" ->
                    () -> toRun(http1Response, () -> setup.http1SleekHandler().handleTRACE(http1Request, http1Response));

            case "CONNECT" ->
                    () -> toRun(http1Response, () -> setup.http1SleekHandler().handleCONNECT(http1Request, http1Response));

            default -> () -> Http1Responder.replyMethodNotAllowed(method, setup.httpMethodList(), http1Response);
        };
    }

    private void toRun(final Http1Response response, final HttpRouterRunnable handler) {

        try {
            handler.run();

        } catch (final HttpSleekException exception) {

            if (!response.getCtx().channel().isActive() || Thread.currentThread().isInterrupted()) {
                return;
            }

            if (response.isCommitted()) {
                response.getCtx().close();
                return;
            }

            response.addHeader(HttpHeaderNames.CONTENT_TYPE, exception.getContentType().getMimeType());

            response.reply(exception.getResponseMessage(), exception.getHttpResponseStatus());

        } catch (final InterruptedException exception) {
            // Timeout or disconnection can interrupt the virtual thread.
            // Do not send 500 in response to a cancellation.
            Thread.currentThread().interrupt();
            response.getCtx().close();

        } catch (final Exception exception) {
            // The channel may have been closed while the handler was working.
            if (!response.getCtx().channel().isActive() || Thread.currentThread().isInterrupted()) {
                return;
            }

            LOGGER.error("HTTP/1 handler execution failed", exception);

            if (response.isCommitted()) {
                response.getCtx().close();
                return;
            }

            response.reply("Internal Server Error", HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) {

        final Http1RequestAdmissionController.Permit permit = Http1RequestAdmissionHandler.take(ctx);

        if (permit == null) {

            LOGGER.error("HTTP/1 request received without admission permit");

            ctx.close();
            return;
        }

        final HttpVersion version = msg.protocolVersion();
        final HttpMethod method = msg.method();

        try {

            if (msg.decoderResult().isFailure()) {

                enqueueError(ctx, version, method, HttpResponseStatus.BAD_REQUEST, "Bad Request", permit);

                return;
            }

            final QueryStringDecoder decoder = new QueryStringDecoder(msg.uri());

            final String path = decoder.path();

            final Http1Setup setup = this.handlers.get(path);

            if (setup != null) {

                execute(ctx, getTask(ctx, setup, msg, decoder), permit);

                return;
            }

            final Path requestedPath = Path.of(path).toAbsolutePath().normalize();

            for (final Map.Entry<Path, Http1Setup> entry : this.fileHandlers.entrySet()) {

                if (requestedPath.startsWith(entry.getKey())) {

                    execute(ctx, getTask(ctx, entry.getValue(), msg, decoder), permit);

                    return;
                }
            }

            enqueueError(ctx, version, method, HttpResponseStatus.NOT_FOUND, "Not Found", permit);

        } catch (final HttpSleekException exception) {

            enqueueError(ctx, version, method, exception.getHttpResponseStatus(), exception.getResponseMessage(), permit);

        } catch (final InvalidPathException exception) {

            enqueueError(ctx, version, method, HttpResponseStatus.BAD_REQUEST, "Bad Request", permit);

        } catch (final RuntimeException exception) {

            LOGGER.error("Unexpected HTTP/1 routing error", exception);

            enqueueError(ctx, version, method, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", permit);
        }
    }
}
