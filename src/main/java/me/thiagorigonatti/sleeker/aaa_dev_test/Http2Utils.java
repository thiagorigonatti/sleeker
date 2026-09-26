/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.aaa_dev_test;

import jakarta.validation.constraints.NotNull;
import me.thiagorigonatti.sleeker.core.http2.Http2Request;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class Http2Utils {

    private static final StringBuilder STRING_BUILDER = new StringBuilder();

    public static void logRequest(@NotNull Http2Request http2Request, @NotNull Logger logger) {

        STRING_BUILDER.setLength(0);

        STRING_BUILDER
                .append("\r\n")
                .append("--------HTTP/2 REQUEST--------")
                .append("\r\n")
                .append("ip_port: ").append(http2Request.remoteAddress().getHostString())
                .append(":").append(http2Request.remoteAddress().getPort())
                .append("\r\n")
                .append("method: ").append(http2Request.method())
                .append("\r\n")
                .append("path: ").append(http2Request.path())
                .append("\r\n");

        for (Map.Entry<CharSequence, CharSequence> header : http2Request.headers()) {
            STRING_BUILDER.append(header.getKey()).append(": ").append(header.getValue())
                    .append("\r\n");
        }

        STRING_BUILDER
                .append(http2Request.body())
                .append("\r\n")
                .append("--------------------------------")
                .append("\r\n");

        logger.info(STRING_BUILDER);
    }
}
