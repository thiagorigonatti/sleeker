/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.aaa_dev_test;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import me.thiagorigonatti.sleeker.core.http1.Http1Request;
import me.thiagorigonatti.sleeker.core.http1.Http1Response;
import me.thiagorigonatti.sleeker.core.http1.Http1SleekHandler;
import me.thiagorigonatti.sleeker.util.ContentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Http1ExampleApiHandler extends Http1SleekHandler {

    private static final Logger LOGGER = LogManager.getLogger(Http1ExampleApiHandler.class);

    @Override
    protected void handleGET(Http1Request http1Request, Http1Response http1Response) {

        http1Response.addHeader(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType());
        http1Response.setBody("Hello from HTTP/1.1");
        http1Response.reply(HttpResponseStatus.OK);

        Http1Utils.logRequest(http1Request, LOGGER);
    }

    @Override
    protected void handlePOST(Http1Request http1Request, Http1Response http1Response) {

/*        if (http1Request.body().isEmpty() || http1Request.body().isBlank()) {

            throw new HttpSleekException.BaseBuilder<>().contentType(ContentType.APPLICATION_JSON_UTF8)
                    .httpResponseStatus(HttpResponseStatus.BAD_REQUEST)
                    .responseMessage(new ObjectMapper().writeValueAsString(Map.of("errorMessage", "Body cannot be empty or blank")))
                    .build();
        }*/

        http1Response.addHeader(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType());
        //http1Response.setBody("Saved! (HTTP/1.1)");
        http1Response.reply(HttpResponseStatus.CREATED);

        Http1Utils.logRequest(http1Request, LOGGER);
    }
}
