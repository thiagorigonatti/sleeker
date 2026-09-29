/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http1;

import io.netty.handler.codec.http.HttpMethod;
import me.thiagorigonatti.sleeker.guard.Cors;

import java.util.Set;

public record Http1Setup(Http1SleekHandler http1SleekHandler, Set<HttpMethod> httpMethodList, Cors cors) {
}
