/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.guard;


import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.AsciiString;

import java.util.Set;

public record Cors(String allowedOrigin, Set<HttpMethod> allowedMethods, Set<AsciiString> allowedHttpHeaders,
                   Boolean allowCredentials, Long maxAge) {
}


