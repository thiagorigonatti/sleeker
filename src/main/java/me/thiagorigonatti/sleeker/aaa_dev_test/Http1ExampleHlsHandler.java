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

import java.nio.file.Files;
import java.nio.file.Path;

public class Http1ExampleHlsHandler extends Http1SleekHandler {

    private static final Logger LOGGER = LogManager.getLogger(Http1ExampleHlsHandler.class);

    @Override
    public void handleGET(Http1Request http1Request, Http1Response http1Response) {

        Path path = Path.of(http1Request.path());

        String ext = path.toString().substring(path.toString().lastIndexOf(".") + 1);

        CharSequence cache = "no-cache";

        LOGGER.info("MISS");

        if (ext.equalsIgnoreCase("mp4")) cache = "public, max-age=86400";

        else if (ext.equalsIgnoreCase("m4s")) cache = "public, max-age=31536000, immutable";

        http1Response.addHeader(HttpHeaderNames.CONTENT_TYPE, ContentType.byExtension(ext));
        http1Response.addHeader(HttpHeaderNames.CACHE_CONTROL, cache);
        try {
            http1Response.replyFileChunked(path, 0, Files.size(path), HttpResponseStatus.OK);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
