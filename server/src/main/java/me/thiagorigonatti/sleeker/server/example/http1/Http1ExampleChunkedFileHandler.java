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

import java.nio.file.Path;

public class Http1ExampleChunkedFileHandler extends Http1SleekHandler {

    @Override
    public void handleGET(Http1Request http1Request, Http1Response http1Response) {
        Path path = Path.of(http1Request.getPath());
        http1Response.addHeader(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
        http1Response.replyFileChunked(path, HttpResponseStatus.OK);
    }
}
