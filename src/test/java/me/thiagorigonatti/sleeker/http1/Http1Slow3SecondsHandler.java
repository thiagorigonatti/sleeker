/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.http1;

import io.netty.handler.codec.http.HttpResponseStatus;
import me.thiagorigonatti.sleeker.core.http1.Http1Request;
import me.thiagorigonatti.sleeker.core.http1.Http1Response;
import me.thiagorigonatti.sleeker.core.http1.Http1SleekHandler;

public class Http1Slow3SecondsHandler extends Http1SleekHandler {

    @Override
    protected void handleGET(Http1Request request, Http1Response response) throws Exception {
        Thread.sleep(3000);
        response.reply("Slow 3 seconds response", HttpResponseStatus.OK);
    }
}
