/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.aaa_dev_test;


import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import me.thiagorigonatti.sleeker.core.http2.Http2Request;
import me.thiagorigonatti.sleeker.core.http2.Http2Response;
import me.thiagorigonatti.sleeker.core.http2.Http2SleekHandler;
import me.thiagorigonatti.sleeker.exception.HttpSleekException;
import me.thiagorigonatti.sleeker.util.ContentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

public class Http2ExampleApiHandler extends Http2SleekHandler {

    private static final Logger LOGGER = LogManager.getLogger(Http2ExampleApiHandler.class);

    @Override
    protected void handleGET(Http2Request http2Request, Http2Response http2Response) {

        http2Response.addHeader(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType());
        http2Response.setBody("Hello from HTTP/2");
        http2Response.reply(HttpResponseStatus.OK);

        Http2Utils.logRequest(http2Request, LOGGER);
    }

    @Override
    protected void handlePOST(Http2Request http2Request, Http2Response http2Response) {

        if (http2Request.body().isEmpty() || http2Request.body().isBlank()) {

            throw new HttpSleekException.BaseBuilder<>()
                    .contentType(ContentType.APPLICATION_JSON_UTF8)
                    .httpResponseStatus(HttpResponseStatus.BAD_REQUEST)
                    .responseMessage(new ObjectMapper().writeValueAsString(Map.of("errorMessage", "Body cannot be empty or blank")))
                    .build();
        }

        http2Response.addHeader(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType());
        http2Response.setBody("Saved! (HTTP/2)");
        http2Response.reply(HttpResponseStatus.CREATED);

        Http2Utils.logRequest(http2Request, LOGGER);
    }
}
