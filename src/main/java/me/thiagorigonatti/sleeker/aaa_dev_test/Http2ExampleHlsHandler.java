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
import me.thiagorigonatti.sleeker.util.ContentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Http2ExampleHlsHandler extends Http2SleekHandler {

    private static final Logger LOGGER = LogManager.getLogger(Http2ExampleHlsHandler.class);

    @Override
    public void handleGET(Http2Request http2Request, Http2Response http2Response) {

        Path path = Path.of(http2Request.path());

        String ext = path.toString().substring(path.toString().lastIndexOf(".") + 1);

        CharSequence cache = "no-cache";

        LOGGER.info("MISS");

        if (ext.equalsIgnoreCase("mp4")) cache = "public, max-age=86400";

        else if (ext.equalsIgnoreCase("m4s")) cache = "public, max-age=31536000, immutable";

        http2Response.addHeader(HttpHeaderNames.CONTENT_TYPE, ContentType.byExtension(ext));
        http2Response.addHeader(HttpHeaderNames.CACHE_CONTROL, cache);
        try {
            http2Response.replyFileChunked(path, 0, Files.size(path), HttpResponseStatus.OK);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
