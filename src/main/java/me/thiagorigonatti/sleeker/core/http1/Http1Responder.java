/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http1;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import me.thiagorigonatti.sleeker.util.ContentType;

import java.nio.charset.StandardCharsets;

public class Http1Responder {

    private Http1Responder() {
        throw new AssertionError("Instantiation of an utility class");
    }

    public static void reply(ChannelHandlerContext ctx, FullHttpRequest msg, HttpResponseStatus httpResponseStatus) {

        boolean end = msg.method().equals(HttpMethod.HEAD);

        ByteBuf body = end ? ctx.alloc().buffer(0) : ctx.alloc().buffer();
        if (!end) {
            body.writeCharSequence(httpResponseStatus.reasonPhrase(), StandardCharsets.UTF_8);
        }

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                httpResponseStatus,
                body
        );

        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType())
                .setInt(HttpHeaderNames.CONTENT_LENGTH, end ? 0 : body.readableBytes());

        ctx.writeAndFlush(response);
    }

    public static void replyNotImplemented(Http1Request http1Request, Http1Response http1Response) {

        boolean end = http1Request.method().equals(HttpMethod.HEAD);

        ChannelHandlerContext ctx = http1Response.getCtx();

        ByteBuf body = end ? ctx.alloc().buffer(0) : ctx.alloc().buffer();
        if (!end) {
            body.writeCharSequence(HttpResponseStatus.NOT_IMPLEMENTED.reasonPhrase(), StandardCharsets.UTF_8);
        }

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.NOT_IMPLEMENTED,
                body
        );

        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType())
                .setInt(HttpHeaderNames.CONTENT_LENGTH, end ? 0 : body.readableBytes());

        ctx.writeAndFlush(response);
    }
}
