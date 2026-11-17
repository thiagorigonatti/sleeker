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

import java.nio.file.Path;

public class Http2ExampleChunkedFileHandler extends Http2SleekHandler {

    @Override
    public void handleGET(Http2Request http2Request, Http2Response http2Response) {
        Path path = Path.of(http2Request.getPath());
        http2Response.addHeader(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
        http2Response.replyFileChunked(path, HttpResponseStatus.OK);
    }
}
