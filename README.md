# SLEEKER v0.0.10
![](assets/png/logo-v0.0.10.png)

[TEST](TEST.md)
```java
public class Test {
    public static void main(String[] args) throws Exception {

        // Creating instances of Http1 and Http2 api handler classes.
        final Http1ExampleApiHandler http1ExampleApiHandler = new Http1ExampleApiHandler();
        final Http2ExampleApiHandler http2ExampleApiHandler = new Http2ExampleApiHandler();

        // Creating instances of Http1 file handler classes.
        final Http1ExampleHlsHandler http1ExampleHlsHandler = new Http1ExampleHlsHandler();

        //Creating Cors instance with origin, allowed methods, allowed headers, sending cookies, and cache time.
        final Cors cors = new Cors("http://localhost:54321",
                Set.of(HttpMethod.GET, HttpMethod.POST),
                Set.of(HttpHeaderNames.AUTHORIZATION), true, 3600L);

        // Creates a builder object for SleekerServer.
        new SleekerServer.Builder()
                // Adds an HTTP context, with an endpoint, a handler that will process the request,
                // and supported HTTP methods.
                .addHttp1Context("/http1_get_post", http1ExampleApiHandler,
                        HttpMethod.GET,
                        HttpMethod.valueOf("CUSTOM"),
                        HttpMethod.POST)

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

                .serveHttp1Files("/tmp/transformers_2007_1080p_br_remux_to_hls/", http1ExampleHlsHandler, HttpMethod.GET)
                .serveHttp1Files("/tmp/hls-simple-player/", http1ExampleHlsHandler, HttpMethod.GET)

                // Builds a SleekerServer object.
                .build()

                // Starts the server with the address and port, as well as the type of I/O used.
                .startServer(new InetSocketAddress("localhost", 8080), ServerIo.TYPE_IOURING);
    }
}
```
```md
2025-09-29 10:00:01 [INFO ] [main] m.t.s.c.SleekerServer: Sleeker server running at: http://localhost:8080
```
### HTTP1.1 HANDLER
```java
public class Http1ExampleApiHandler extends Http1SleekHandler {

    private static final Logger LOGGER = LogManager.getLogger(Http1ExampleApiHandler.class);

    @Override
    protected void handleGET(Http1Request http1Request, Http1Response http1Response) {

        http1Response.addHeader(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType());
        http1Response.setBody("Hello from HTTP/1.1");
        http1Response.reply(HttpResponseStatus.OK);

        Http1Utils.logRequest(http1Request, LOGGER);
    }

    @Override
    protected void handlePOST(Http1Request http1Request, Http1Response http1Response) {

        if (http1Request.body().isEmpty() || http1Request.body().isBlank()) {

            throw new HttpSleekException.BaseBuilder<>().contentType(ContentType.APPLICATION_JSON_UTF8)
                    .httpResponseStatus(HttpResponseStatus.BAD_REQUEST)
                    .responseMessage(new ObjectMapper().writeValueAsString(Map.of("errorMessage", "Body cannot be empty or blank")))
                    .build();
        }

        http1Response.addHeader(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType());
        http1Response.setBody("Saved! (HTTP/1.1)");
        http1Response.reply(HttpResponseStatus.CREATED);

        Http1Utils.logRequest(http1Request, LOGGER);
    }
}
```
### HTTP1.1 REQUEST
```md
2026-09-26 09:45:44 [INFO ] [pool-2-thread-2] m.t.s.a.Http1ExampleApiHandler:
--------HTTP/1.1 REQUEST--------
ip_port: 127.0.0.1:43542
method: POST
path: /http1_get_post
Postman-Token: c84465e3-48aa-4337-afba-9f0739627608
Content-Type: application/json
Content-Length: 37
Host: localhost:8080
User-Agent: PostmanRuntime/2.6.0
Accept: */*
Accept-Encoding: gzip, deflate, br
Connection: keep-alive
{
"id": "abc",
"level": 123
}
--------------------------------
```
### HTTP2 HANDLER
```java
public class Http2ExampleApiHandler extends Http2SleekHandler {

    private static final Logger LOGGER = LogManager.getLogger(Http2ExampleApiHandler.class);

    @Override
    protected void handleGET(Http2Request http2Request, Http2Response http2Response) {

        http2Response.addHeader(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType());
        http2Response.setBody("Hello from HTTP/2");
        http2Response.reply(HttpResponseStatus.OK);

        Http2Utils.logRequest(http2Request, LOGGER);
    }

    @Override
    protected void handlePOST(Http2Request http2Request, Http2Response http2Response) {

        if (http2Request.body().isEmpty() || http2Request.body().isBlank()) {

            throw new HttpSleekException.BaseBuilder<>()
                    .contentType(ContentType.APPLICATION_JSON_UTF8)
                    .httpResponseStatus(HttpResponseStatus.BAD_REQUEST)
                    .responseMessage(new ObjectMapper().writeValueAsString(Map.of("errorMessage", "Body cannot be empty or blank")))
                    .build();
        }

        http2Response.addHeader(HttpHeaderNames.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8.getMimeType());
        http2Response.setBody("Saved! (HTTP/2)");
        http2Response.reply(HttpResponseStatus.CREATED);

        Http2Utils.logRequest(http2Request, LOGGER);
    }
}
```
### HTTP2 REQUEST
```md
2026-09-26 09:47:01 [INFO ] [pool-2-thread-3] m.t.s.a.Http2ExampleApiHandler:
--------HTTP/2 REQUEST--------
ip_port: 127.0.0.1:49096
method: POST
path: /http2_post
:path: /http2_post
:method: POST
:authority: localhost:8080
:scheme: https
postman-token: d7e16a96-4265-4bf3-a4e2-e5383e92246b
content-type: application/json
user-agent: PostmanRuntime/2.6.0
accept: */*
accept-encoding: gzip, deflate, br
content-length: 37
{
"id": "abc",
"level": 123
}
--------------------------------
```
