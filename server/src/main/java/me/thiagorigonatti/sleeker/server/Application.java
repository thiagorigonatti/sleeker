/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.server;

import io.netty.handler.codec.http.HttpMethod;
import me.thiagorigonatti.sleeker.core.SleekerServer;
import me.thiagorigonatti.sleeker.io.ServerIo;
import me.thiagorigonatti.sleeker.server.example.http1.Http1ExampleApiHandler;

import java.net.InetSocketAddress;

public class Application {

    static void main() throws Exception {

        new SleekerServer.Builder()
                //.withSsl(Path.of("server/tls/localhost-cert.pem"), Path.of("server/tls/localhost-key.pem"))
                .addHttp1Context("/http1_get_post", new Http1ExampleApiHandler(), HttpMethod.GET, HttpMethod.POST)
                .build()
                .startServer(new InetSocketAddress("0.0.0.0", 8080), ServerIo.TYPE_IOURING);
    }
}
