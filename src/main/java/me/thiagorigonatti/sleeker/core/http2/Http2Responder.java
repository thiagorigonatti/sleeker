/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.*;
import io.netty.util.CharsetUtil;
import me.thiagorigonatti.sleeker.util.ContentType;

public class Http2Responder {

    private Http2Responder() {
        throw new AssertionError("Instantiation of an utility class");
    }

    public static void reply(ChannelHandlerContext ctx, Http2Headers http2Headers,
                             Http2FrameStream stream, HttpResponseStatus httpResponseStatus) {

        Http2Headers headers = new DefaultHttp2Headers()
                .status(httpResponseStatus.codeAsText())
                .set(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType());

        HttpMethod httpMethod = HttpMethod.valueOf(http2Headers.method().toString());

        boolean end = httpMethod.equals(HttpMethod.HEAD);

        ctx.write(new DefaultHttp2HeadersFrame(headers, end).stream(stream));

        if (!end) {
            ByteBuf body = ctx.alloc().buffer();
            body.writeCharSequence(httpResponseStatus.reasonPhrase(), CharsetUtil.UTF_8);
            ctx.write(new DefaultHttp2DataFrame(body, true).stream(stream));
        }

        ctx.flush();
    }

    public static void replyNotImplemented(Http2Request http2Request, Http2Response http2Response) {
        http2Response.addHeader(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType());
        http2Response.setBody(HttpResponseStatus.NOT_IMPLEMENTED.reasonPhrase());
        http2Response.reply(HttpResponseStatus.NOT_IMPLEMENTED);
    }
}
