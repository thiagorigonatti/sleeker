/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.aaa_dev_test;

import me.thiagorigonatti.sleeker.core.http1.Http1Request;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.Map;

public class Http1Utils {

    private static final StringBuilder STRING_BUILDER = new StringBuilder();

    public static void logRequest(Http1Request http1Request, Logger logger) {

        STRING_BUILDER.setLength(0);

        STRING_BUILDER
                .append("\r\n")
                .append("--------HTTP/1.1 REQUEST--------");

        if (http1Request.remoteAddress() instanceof InetSocketAddress inetSocketAddress) {
            STRING_BUILDER
                    .append("\r\n")
                    .append("ip_port: ").append(inetSocketAddress.getHostString()).append(":").append(inetSocketAddress.getPort());
        }

        STRING_BUILDER
                .append("\r\n")
                .append("method: ").append(http1Request.method())
                .append("\r\n")
                .append("path: ").append(http1Request.path())
                .append("\r\n");


        for (Map.Entry<String, String> header : http1Request.headers()) {
            STRING_BUILDER
                    .append(header.getKey()).append(": ").append(header.getValue())
                    .append("\r\n");
        }

        STRING_BUILDER
                .append(http1Request.body())
                .append("\r\n")
                .append("--------------------------------")
                .append("\r\n");

        logger.info(STRING_BUILDER);
    }
}
