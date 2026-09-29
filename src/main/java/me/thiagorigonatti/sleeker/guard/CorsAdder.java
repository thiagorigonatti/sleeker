/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.guard;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import me.thiagorigonatti.sleeker.core.HeaderAddeable;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.stream.Collectors;

public class CorsAdder {

    private CorsAdder() {
        throw new AssertionError("Instantiation of an utility class");
    }

    public static void addCors(@NonNull final Cors cors, @NonNull final HeaderAddeable headerAddeable) {

        final Cors nonNullCors = Objects.requireNonNull(cors);

        final HeaderAddeable nonNullHeaderAddeable = Objects.requireNonNull(headerAddeable);

        final String methods = Objects.requireNonNull(nonNullCors.allowedMethods()).stream().map(HttpMethod::name)
                .collect(Collectors.joining(", "));

        final String headers = String.join(", ", Objects.requireNonNull(nonNullCors.allowedHttpHeaders()));

        nonNullHeaderAddeable.addHeader(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN,
                Objects.requireNonNull(nonNullCors.allowedOrigin()));

        nonNullHeaderAddeable.addHeader(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, methods);
        nonNullHeaderAddeable.addHeader(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, headers);

        nonNullHeaderAddeable.addHeader(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                Objects.requireNonNull(nonNullCors.allowCredentials()).toString());

        nonNullHeaderAddeable.addHeader(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE,
                Objects.requireNonNull(nonNullCors.maxAge()).toString());
    }
}
