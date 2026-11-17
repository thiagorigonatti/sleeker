/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.server.example.http2;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import me.thiagorigonatti.sleeker.core.http2.Http2Request;
import me.thiagorigonatti.sleeker.core.http2.Http2Response;
import me.thiagorigonatti.sleeker.core.http2.Http2SleekHandler;
import me.thiagorigonatti.sleeker.util.ContentType;
import me.thiagorigonatti.sleeker.util.Http2Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Http2ExampleApiHandler extends Http2SleekHandler {

    private static final Logger LOGGER = LogManager.getLogger(Http2ExampleApiHandler.class);

    @Override
    protected void handleGET(Http2Request http1Request, Http2Response http2Response) {
        http2Response.addHeader(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType());
        http2Response.reply("Hello from HTTP/2", HttpResponseStatus.OK);
        Http2Utils.logRequest(http1Request, LOGGER);
    }

    @Override
    protected void handlePOST(Http2Request http2Request, Http2Response http2Response) {
        http2Response.addHeader(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType());
        http2Response.reply("Saved! (HTTP/2)", HttpResponseStatus.CREATED);
        Http2Utils.logRequest(http2Request, LOGGER);
    }
}
