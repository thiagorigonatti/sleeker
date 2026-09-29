/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.aaa_dev_test;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import me.thiagorigonatti.sleeker.core.SleekerServer;
import me.thiagorigonatti.sleeker.guard.Cors;
import me.thiagorigonatti.sleeker.io.ServerIo;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Set;

public class Test {
    public static void main(String[] args) throws Exception {

        // Creating instances of Http1 and Http2 api handler classes.
        final Http1ExampleApiHandler http1ExampleApiHandler = new Http1ExampleApiHandler();
        final Http2ExampleApiHandler http2ExampleApiHandler = new Http2ExampleApiHandler();

        // Creating instances of Http1 file handler classes.
        final Http1ExampleHlsHandler http1ExampleHlsHandler = new Http1ExampleHlsHandler();
        final Http2ExampleHlsHandler http2ExampleHlsHandler = new Http2ExampleHlsHandler();

        //Creating Cors instance with origin, allowed methods, allowed headers, sending cookies, and cache time.
        final Cors cors = new Cors("http://localhost:61342",
                Set.of(HttpMethod.GET, HttpMethod.POST),
                Set.of(HttpHeaderNames.AUTHORIZATION), true, 3600L);

        final Cors endPointCors = new Cors("http://localhost:63342",
                Set.of(HttpMethod.GET, HttpMethod.POST),
                Set.of(HttpHeaderNames.AUTHORIZATION), true, 3600L);

        final Cors fileCors = new Cors("http://localhost:63342", Set.of(HttpMethod.GET),
                Set.of(), true, 3600L);

        // Creates a builder object for SleekerServer.
        new SleekerServer.Builder()
                // Adds an HTTP context, with an endpoint, a handler that will process the request,
                // and supported HTTP methods.
                .addHttp1Context("/cartoon", http1ExampleApiHandler, endPointCors, HttpMethod.GET, HttpMethod.POST)

                .addHttp1Context("/http1_put_patch_delete", http1ExampleApiHandler,
                        HttpMethod.PUT,
                        HttpMethod.PATCH,
                        HttpMethod.DELETE)

                .addHttp1Context("/http1_head", http1ExampleApiHandler, HttpMethod.HEAD)

                // Adds CORS for both http1 and http2
                .withCors(cors)

                // Configures SSL with cert file and private key.
                .withSsl(Path.of("localhost-cert.pem"), Path.of("localhost-key.pem"))

                .addHttp2Context("/http2_get", http2ExampleApiHandler, HttpMethod.GET)
                .addHttp2Context("/http2_post", http2ExampleApiHandler, HttpMethod.POST)

                .serveHttp1Files("/tmp/hls-simple-player/", http1ExampleHlsHandler, fileCors, HttpMethod.GET)

                .serveHttp2Files("/tmp/transformers_2007_1080p_br_remux_to_hls/", http2ExampleHlsHandler, fileCors, HttpMethod.GET)

                // Builds a SleekerServer object.
                .build()

                // Starts the server with the address and port, as well as the type of I/O used.
                .startServer(new InetSocketAddress("localhost", 8080), ServerIo.TYPE_IOURING);
    }
}
