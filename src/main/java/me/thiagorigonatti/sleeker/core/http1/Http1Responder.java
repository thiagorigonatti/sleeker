/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http1;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import me.thiagorigonatti.sleeker.util.ContentType;

import java.util.Set;

public class Http1Responder {

    private Http1Responder() {
        throw new AssertionError("Instantiation of an utility class");
    }


    public static void reply(final ChannelHandlerContext ctx, final FullHttpRequest msg, final HttpResponseStatus httpResponseStatus) {

        final Http1Response http1Response = new Http1Response(ctx, msg.protocolVersion());

        final String body = msg.method().equals(HttpMethod.HEAD) ? "" : httpResponseStatus.reasonPhrase();

        http1Response.addHeader(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType());

        http1Response.reply(body, httpResponseStatus);
    }


    public static void replyMethodNotAllowed(final HttpMethod method, final Set<HttpMethod> allowedMethods, final Http1Response http1Response) {

        final String body = method.equals(HttpMethod.HEAD) ? "" : HttpResponseStatus.METHOD_NOT_ALLOWED.reasonPhrase();

        http1Response.addHeader(HttpHeaderNames.ALLOW, String.join(", ", allowedMethods.stream().map(HttpMethod::asciiName).toList()));

        http1Response.addHeader(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType());

        http1Response.reply(body, HttpResponseStatus.METHOD_NOT_ALLOWED);
    }


    public static void replyNotImplemented(final Http1Request http1Request, final Http1Response http1Response) {

        final String body = http1Request.getMethod().equals(HttpMethod.HEAD) ? "" : HttpResponseStatus.NOT_IMPLEMENTED.reasonPhrase();

        http1Response.addHeader(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType());

        http1Response.reply(body, HttpResponseStatus.NOT_IMPLEMENTED);
    }

}
