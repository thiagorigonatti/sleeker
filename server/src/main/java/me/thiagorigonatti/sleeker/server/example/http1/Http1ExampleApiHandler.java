/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.server.example.http1;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import me.thiagorigonatti.sleeker.core.http1.Http1Request;
import me.thiagorigonatti.sleeker.core.http1.Http1Response;
import me.thiagorigonatti.sleeker.core.http1.Http1SleekHandler;
import me.thiagorigonatti.sleeker.util.ContentType;

import java.util.concurrent.TimeUnit;

public class Http1ExampleApiHandler extends Http1SleekHandler {

    @Override
    protected void handleGET(Http1Request http1Request, Http1Response http1Response) throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(1);
        http1Response.addHeader(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType());
        http1Response.reply("Hello from HTTP/1.1", HttpResponseStatus.OK);
    }

    @Override
    protected void handlePOST(Http1Request http1Request, Http1Response http1Response) {
        http1Response.addHeader(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType());
        http1Response.reply("Saved! (HTTP/1.1)", HttpResponseStatus.CREATED);
    }
}
