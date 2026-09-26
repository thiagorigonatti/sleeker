/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.guard;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import me.thiagorigonatti.sleeker.core.HeaderAddeable;

import java.util.stream.Collectors;

public class CorsAdder {

    private CorsAdder() {
        throw new AssertionError("Instantiation of an utility class");
    }

    public static void addCors(Cors cors, HeaderAddeable headerAddeable) {

        String methods = cors.allowedMethods().stream().map(HttpMethod::name)
                .collect(Collectors.joining(", "));

        String headers = String.join(", ", cors.allowedHttpHeaders());

        headerAddeable.addHeader(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, cors.allowedOrigin());
        headerAddeable.addHeader(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, methods);
        headerAddeable.addHeader(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, headers);
        headerAddeable.addHeader(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, cors.allowCredentials().toString());
        headerAddeable.addHeader(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE, cors.maxAge().toString());
    }
}
