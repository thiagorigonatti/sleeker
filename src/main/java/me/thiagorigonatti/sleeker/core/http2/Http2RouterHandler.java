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
import io.netty.handler.codec.http2.*;
import io.netty.util.CharsetUtil;
import me.thiagorigonatti.sleeker.core.HttpRouterRunnable;
import me.thiagorigonatti.sleeker.core.SleekerServer;
import me.thiagorigonatti.sleeker.exception.Http2SleekException;
import me.thiagorigonatti.sleeker.guard.Cors;
import me.thiagorigonatti.sleeker.guard.CorsAdder;
import me.thiagorigonatti.sleeker.util.ContentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ChannelHandler.Sharable
public class Http2RouterHandler extends SimpleChannelInboundHandler<Http2Frame> {

    private static final Logger LOGGER = LogManager.getLogger(Http2RouterHandler.class);
    private Cors cors;
    private boolean corsEnabled;

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public boolean isCorsEnabled() {
        return corsEnabled;
    }

    public void setCorsEnabled(boolean corsEnabled) {
        this.corsEnabled = corsEnabled;
    }

    public final Map<String, Http2Setup> handlers = new HashMap<>();
    public final Map<String, Http2Setup> fileHandlers = new HashMap<>();
    private final Map<String, Http2HeaderHolder> http2RequestMap = new ConcurrentHashMap<>();

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {

        if (cause instanceof Http2SleekException http2SleekException) {

            Http2Headers http2Headers = http2SleekException.getHttp2Headers();
            Http2FrameStream http2FrameStream = http2SleekException.getHttp2FrameStream();

            HttpResponseStatus httpResponseStatus = http2SleekException.getHttpResponseStatus();
            CharSequence responseMessage = http2SleekException.getResponseMessage();
            ContentType contentType = http2SleekException.getContentType();

            if (ctx.channel().isActive()) {
                Http2Headers headers = new DefaultHttp2Headers()
                        .status(httpResponseStatus.codeAsText())
                        .set(HttpHeaderNames.CONTENT_TYPE, contentType.getMimeType());

                HttpMethod httpMethod = HttpMethod.valueOf(http2Headers.method().toString());
                boolean end = httpMethod.equals(HttpMethod.HEAD);

                ctx.write(new DefaultHttp2HeadersFrame(headers, end).stream(http2FrameStream));

                if (!end) {
                    ByteBuf body = ctx.alloc().buffer();
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
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void toRun(ChannelHandlerContext ctx, HttpRouterRunnable handler) {
        try {
            handler.run();
        } catch (Exception e) {
            LOGGER.warn(e.getMessage());
            exceptionCaught(ctx, e);
        }
    }

    private Runnable getTask(ChannelHandlerContext ctx, Http2Setup setup, Http2Headers headers, String body, Http2Frame http2Frame) {

        final Http2FrameStream stream = http2Frame instanceof Http2HeadersFrame http2HeadersFrame
                ? http2HeadersFrame.stream()
                : ((Http2DataFrame) http2Frame).stream();

        final Http2Response http2Response = new Http2Response(ctx, stream, HttpMethod.valueOf(headers.method().toString()));

        if (setup.cors() != null) {
            setup.httpMethodList().add(HttpMethod.OPTIONS);
            CorsAdder.addCors(setup.cors(), http2Response);

        } else if (this.isCorsEnabled()) {
            CorsAdder.addCors(this.getCors(), http2Response);
        }

        if (!setup.httpMethodList().contains(HttpMethod.valueOf(headers.method().toString()))) {
            return () -> Http2Responder.reply(ctx, headers, stream, HttpResponseStatus.METHOD_NOT_ALLOWED);
        }

        final Http2Request http2Request = new Http2Request(
                (InetSocketAddress) ctx.channel().localAddress(),
                (InetSocketAddress) ctx.channel().remoteAddress(),
                headers, body, stream, headers.path().toString(),
                HttpMethod.valueOf(headers.method().toString()));

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
                    http2Response.reply(HttpResponseStatus.NO_CONTENT);
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

    private void performEnd(ChannelHandlerContext ctx, Http2Headers headers, Http2Setup http2Setup,
                            Http2HeaderHolder context, Http2DataFrame dataFrame, String id) {

        SleekerServer.EXECUTOR_SERVICE.execute(getTask(ctx, http2Setup, headers,
                context.body().toString(CharsetUtil.UTF_8), dataFrame));

        context.body().release();
        http2RequestMap.remove(id);
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Http2Frame msg) {

        if (msg instanceof Http2HeadersFrame headersFrame) {

            String id = ctx.channel().id().asShortText() + "_" + headersFrame.stream().id();

            String path = headersFrame.headers().path().toString();

            Http2Setup http2Setup = handlers.get(path);

            if (http2Setup == null) {
                for (String s : fileHandlers.keySet()) {
                    if (path.startsWith(s) && Files.exists(Path.of(path))) {
                        http2Setup = fileHandlers.get(s);
                        break;
                    }
                }
            }

            if (http2Setup == null) {
                SleekerServer.EXECUTOR_SERVICE.execute(() -> Http2Responder
                        .reply(ctx, headersFrame.headers(), headersFrame.stream(), HttpResponseStatus.NOT_FOUND));
                return;
            }

            if (headersFrame.isEndStream()) {
                SleekerServer.EXECUTOR_SERVICE.execute(getTask(ctx, http2Setup, headersFrame.headers(), "", headersFrame));
            } else {
                ByteBuf body = ctx.alloc().buffer();
                http2RequestMap.put(id, new Http2HeaderHolder(headersFrame.headers(), body));
            }
        } else if (msg instanceof Http2DataFrame dataFrame) {

            String id = ctx.channel().id().asShortText() + "_" + dataFrame.stream().id();
            Http2HeaderHolder context = http2RequestMap.get(id);

            if (context == null) {
                return;
            }

            context.body().writeBytes(dataFrame.content());
            Http2Headers headers = context.headers();

            if (handlers.get(headers.path().toString()) != null) {
                Http2Setup http2Setup = handlers.get(headers.path().toString());

                if (dataFrame.isEndStream())
                    performEnd(ctx, headers, http2Setup, context, dataFrame, id);

            } else {
                for (String s : fileHandlers.keySet()) {
                    if (headers.path().toString().startsWith(s) && Files.exists(Path.of(headers.path().toString()))) {
                        Http2Setup http2FileSetup = fileHandlers.get(s);

                        if (dataFrame.isEndStream())
                            performEnd(ctx, headers, http2FileSetup, context, dataFrame, id);
                        break;
                    }
                }
            }

        } else if (msg instanceof Http2ResetFrame resetFrame) {
            String id = ctx.channel().id().asShortText() + "_" + resetFrame.stream().id();
            Http2HeaderHolder req = http2RequestMap.remove(id);
            if (req != null) {
                req.body().release();
            }
        }
    }
}
