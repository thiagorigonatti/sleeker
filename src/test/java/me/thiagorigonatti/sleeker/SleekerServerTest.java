/*
 * Copyright (c) 2025. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import me.thiagorigonatti.sleeker.core.SleekerServer;
import me.thiagorigonatti.sleeker.handler.http1.Http1TestHandler;
import me.thiagorigonatti.sleeker.handler.http2.Http2TestHandler;
import me.thiagorigonatti.sleeker.io.ServerIo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SleekerServerTest {

    private final Http1TestHandler http1TestHandler = new Http1TestHandler();

    @BeforeAll
    void setUp() throws Exception {
        final SleekerServer sleekerServer = new SleekerServer.Builder()
                .addHttp1Context("/http1_test", http1TestHandler,
                        HttpMethod.GET,
                        HttpMethod.POST)
                .build();

        sleekerServer.startServer(new InetSocketAddress("localhost", 7357), ServerIo.TYPE_IOURING);
    }

    private HttpResponse<String> sendTestRequest(final String method, final CharSequence path) {
        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:7357" + path))
                    .method(HttpMethod.valueOf(method).name(), HttpRequest.BodyPublishers.noBody())
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void givenStarting_whenSetHttp2_but_noSslContext_shouldAbort() {
        assertThrows(IllegalStateException.class, () -> new SleekerServer.Builder()
                .addHttp2Context("/http2_test", new Http2TestHandler())
                .build());
    }

    @Test
    void givenRequests_whenResponding_shouldStatusBeCorrect() {
        assertEquals(sendTestRequest("GET", "/http1_test").statusCode(), HttpResponseStatus.OK.code());
        assertEquals(sendTestRequest("POST", "/http1_test").statusCode(), HttpResponseStatus.NOT_IMPLEMENTED.code());
        assertEquals(sendTestRequest("DELETE", "/http1_test").statusCode(), HttpResponseStatus.METHOD_NOT_ALLOWED.code());
        assertEquals(sendTestRequest("CUSTOM", "/http1_test").statusCode(), HttpResponseStatus.METHOD_NOT_ALLOWED.code());
    }

    @Test
    void givenRequestsToUnknownEndPoints_whenResponding_shouldStatusBeNotFound() {
        assertEquals(sendTestRequest("GET", "/unknown").statusCode(), HttpResponseStatus.NOT_FOUND.code());
    }

    @Test
    void givenAHeadRequest_whenResponding_shouldBodyBeEmpty() {
        assertTrue(sendTestRequest("HEAD", "/http1_test").body().isEmpty());
    }
}
