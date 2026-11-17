/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.http1;

import io.netty.handler.codec.http.HttpResponseStatus;
import me.thiagorigonatti.sleeker.core.http1.Http1Request;
import me.thiagorigonatti.sleeker.core.http1.Http1Response;
import me.thiagorigonatti.sleeker.core.http1.Http1SleekHandler;

import java.util.concurrent.atomic.AtomicInteger;

public class Http1ConcurrencyHandler extends Http1SleekHandler {

    private static final AtomicInteger ACTIVE = new AtomicInteger();

    private static final AtomicInteger MAX_OBSERVED = new AtomicInteger();

    @Override
    protected void handleGET(Http1Request request, Http1Response response) throws Exception {

        int current = ACTIVE.incrementAndGet();

        MAX_OBSERVED.accumulateAndGet(current, Math::max);

        try {
            Thread.sleep(2000);

            response.reply("Active: " + current + ", Max: " + MAX_OBSERVED.get(), HttpResponseStatus.OK);

        } finally {
            ACTIVE.decrementAndGet();
        }
    }
}