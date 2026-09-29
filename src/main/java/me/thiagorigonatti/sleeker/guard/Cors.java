/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.guard;


import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.AsciiString;
import org.jspecify.annotations.NonNull;

import java.util.Set;

public record Cors(@NonNull String allowedOrigin,
                   @NonNull Set<HttpMethod> allowedMethods,
                   @NonNull Set<AsciiString> allowedHttpHeaders,
                   @NonNull Boolean allowCredentials,
                   @NonNull Long maxAge
) {
}
