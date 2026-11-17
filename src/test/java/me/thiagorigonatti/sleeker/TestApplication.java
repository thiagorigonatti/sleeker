/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker;

import io.netty.handler.codec.http.HttpMethod;
import me.thiagorigonatti.sleeker.core.SleekerServer;
import me.thiagorigonatti.sleeker.http1.*;
import me.thiagorigonatti.sleeker.io.ServerIo;

import java.net.InetSocketAddress;
import java.nio.file.Path;

public class TestApplication {

    static void main() throws Exception {

        new SleekerServer.Builder()

                .withSsl(Path.of("src/test/resources/tls/localhost-cert.pem"),
                        Path.of("src/test/resources/tls/localhost-key.pem"))

                .addHttp1Context("/http1_hello", new Http1HelloHandler(), HttpMethod.GET, HttpMethod.POST)

                .addHttp1Context("/slow5seconds", new Http1Slow5SecondsHandler(), HttpMethod.GET)

                .serveHttp1Files("/home/thiagorigonatti/IdeaProjects/sleeker/src/test/resources/testing-files/", new Http1FileHandler(), HttpMethod.GET)

                .addHttp1Context("/slow8seconds", new Http1Slow8SecondsHandler(), HttpMethod.GET)

                .addHttp1Context("/slow3seconds", new Http1Slow3SecondsHandler(), HttpMethod.GET)

                .addHttp1Context("/concurrency", new Http1ConcurrencyHandler(), HttpMethod.GET)

                .build()

                .startServer(new InetSocketAddress("localhost", 7777), ServerIo.TYPE_EPOLL);

    }

}
