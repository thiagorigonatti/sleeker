/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import me.thiagorigonatti.sleeker.core.http1.*;
import me.thiagorigonatti.sleeker.core.http2.*;
import me.thiagorigonatti.sleeker.guard.Cors;
import me.thiagorigonatti.sleeker.io.ServerIo;
import me.thiagorigonatti.sleeker.io.SleekIo;
import me.thiagorigonatti.sleeker.tls.ServerSsl;
import me.thiagorigonatti.sleeker.util.AsciiArt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class SleekerServer {

    private static final Logger LOGGER = LogManager.getLogger(SleekerServer.class);

    private final Http1RequestAdmissionController http1RequestAdmissionController;
    private final boolean useSsl;
    private final SslContext sslContext;

    private final boolean useHttp1;
    private final boolean useHttp2;

    private final Http1RouterHandler http1RouterHandler;
    private final Http2RouterHandler http2RouterHandler;

    protected SleekerServer(final Builder builder) {

        this.http1RequestAdmissionController =
                new Http1RequestAdmissionController(Config.getHttp1MaxRequests(), 64L * 1024 * 1024);

        this.useSsl = builder.useSsl;
        this.sslContext = builder.sslContext;
        this.useHttp1 = builder.useHttp1;
        this.useHttp2 = builder.useHttp2;

        if (!useSsl && useHttp2) {
            throw new IllegalStateException("HTTP/2 requires SSL/TLS in this configuration, but no valid SSL contextis available. Verify that the SSL context was created correctly, or disable HTTP/2.");
        }

        if (builder.corsEnabled) {
            builder.http1RouterHandler.handlers.forEach((s, http1Setup) ->
                    http1Setup.httpMethodList().add(HttpMethod.OPTIONS));
            builder.http1RouterHandler.fileHandlers.forEach((s, http1Setup) ->
                    http1Setup.httpMethodList().add(HttpMethod.OPTIONS));
            builder.http1RouterHandler.setCorsEnabled(true);
            builder.http1RouterHandler.setCors(builder.cors);


            builder.http2RouterHandler.handlers.forEach((s, http2Setup) ->
                    http2Setup.httpMethodList().add(HttpMethod.OPTIONS));
            builder.http2RouterHandler.fileHandlers.forEach((s, http2Setup) ->
                    http2Setup.httpMethodList().add(HttpMethod.OPTIONS));
            builder.http2RouterHandler.setCorsEnabled(true);
            builder.http2RouterHandler.setCors(builder.cors);
        }

        this.http1RouterHandler = builder.http1RouterHandler;
        this.http1RouterHandler.setHttp1BoundedExecutor(
                new Http1BoundedExecutor(
                        Config.EXECUTOR_SERVICE,
                        Config.getHttp1MaxConcurrentRequests(),
                        Config.getHttp1MaxRequests()
                )
        );

        this.http2RouterHandler = builder.http2RouterHandler;
    }

    public void startServer(final SocketAddress socketAddress, final ServerIo serverIo) throws Exception {

        final SleekIo sleekIo = Config.getSleekIo(serverIo, (socketAddress instanceof UnixDomainSocketAddress
                || socketAddress instanceof DomainSocketAddress));

        IoHandlerFactory ioHandlerFactory = sleekIo.getIoHandlerFactory();

        EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(Config.getBossThreads(), ioHandlerFactory);
        EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(Config.getWorkerThreads(), ioHandlerFactory);
        ConnectionLimiter connectionLimiter = new ConnectionLimiter(Config.getHttp1MaxConnections());

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(sleekIo.getServerChannelClass())
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        pipeline.addLast(connectionLimiter);

                        if (useSsl) {
                            if (sslContext == null) {
                                throw new IllegalStateException("SSL is enabled, but sslContext is null");
                            }

                            pipeline.addLast(sslContext.newHandler(ch.alloc()));

                            if (useHttp2) {
                                pipeline.addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
                                    @Override
                                    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                                        switch (protocol) {
                                            case ApplicationProtocolNames.HTTP_2 ->
                                                    configureHttp2Chunked(ctx.pipeline());
                                            case ApplicationProtocolNames.HTTP_1_1 -> {
                                                if (useHttp1) {
                                                    configureHttp1Chunked(ctx.pipeline());
                                                } else {
                                                    throw new IllegalStateException("HTTP/1.1 not supported by server");
                                                }
                                            }
                                            default ->
                                                    throw new IllegalStateException("Unsupported ALPN protocol: " + protocol);
                                        }
                                    }
                                });
                            } else if (useHttp1) {
                                configureHttp1Chunked(pipeline);
                            }
                        } else if (useHttp1) {
                            configureHttp1(pipeline);
                        }
                    }
                });


        ChannelFuture channelFuture = bootstrap.bind(socketAddress).sync();

        String at = null;
        if (socketAddress instanceof DomainSocketAddress domainSocketAddress) {
            at = domainSocketAddress.path();

        } else if (socketAddress instanceof InetSocketAddress inetSocketAddress) {
            String url = inetSocketAddress.getHostName() + ":" + inetSocketAddress.getPort();
            at = useSsl ? "https://" + url : "http://" + url;
        }

        LOGGER.info("Sleeker server running at: {}", at);

        channelFuture.channel().closeFuture().addListener((ChannelFutureListener) future -> {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        });
    }

    private void configureHttp1(final ChannelPipeline pipeline) {
        final Http1HeaderTimeout headerTimeout = new Http1HeaderTimeout(Config.getHttp1HeaderTimeoutSeconds(), TimeUnit.SECONDS);

        pipeline.addLast(
                new IdleStateHandler(
                        true,
                        0,
                        Config.getHttp1ResponseIdleTimeoutSeconds(),
                        Config.getHttp1ConnectionIdleTimeoutSeconds(),
                        TimeUnit.SECONDS
                ),
                new Http1HeaderDeadlineCodec(headerTimeout),
                new Http1RequestTimeoutHandler(Config.getHttp1RequestTimeoutSeconds(), TimeUnit.SECONDS),
                new HttpServerKeepAliveHandler(),
                new Http1RequestAdmissionHandler(http1RequestAdmissionController),
                new Http1OrderedObjectAggregator(Config.getHttp1MaxContentLength()),
                new Http1ConnectionIdleHandler(),
                http1RouterHandler
        );
    }

    private void configureHttp1Chunked(final ChannelPipeline pipeline) {
        final Http1HeaderTimeout headerTimeout = new Http1HeaderTimeout(Config.getHttp1HeaderTimeoutSeconds(), TimeUnit.SECONDS);

        pipeline.addLast(
                new IdleStateHandler(
                        true,
                        0,
                        Config.getHttp1ResponseIdleTimeoutSeconds(),
                        Config.getHttp1ConnectionIdleTimeoutSeconds(),
                        TimeUnit.SECONDS
                ),
                new Http1HeaderDeadlineCodec(headerTimeout),
                new Http1RequestTimeoutHandler(Config.getHttp1RequestTimeoutSeconds(), TimeUnit.SECONDS),
                new HttpServerKeepAliveHandler(),
                new Http1RequestAdmissionHandler(http1RequestAdmissionController),
                new Http1OrderedObjectAggregator(Config.getHttp1MaxContentLength()),
                new Http1ConnectionIdleHandler(),
                new ChunkedWriteHandler(),
                http1RouterHandler
        );
    }

    private void configureHttp2(final ChannelPipeline pipeline) {
        pipeline.addLast(
                new Http2HeaderTimeoutRawHandler(Config.getHttp2HeaderTimeoutSeconds(), TimeUnit.SECONDS),
                Http2FrameCodecBuilder.forServer()
                        .validateHeaders(true)
                        .validateRequiredPseudoHeaders(true)
                        .initialSettings(new Http2Settings().maxConcurrentStreams(Config.getHttp2MaxConcurrentStreams()))
                        .build(),
                new Http2MultiplexHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(
                                new Http2RequestTimeoutHandler(Config.getHttp2RequestTimeoutSeconds(), TimeUnit.SECONDS),
                                http2RouterHandler
                        );
                    }
                })
        );
    }

    private void configureHttp2Chunked(final ChannelPipeline pipeline) {
        pipeline.addLast(
                new Http2HeaderTimeoutRawHandler(Config.getHttp2HeaderTimeoutSeconds(), TimeUnit.SECONDS),
                Http2FrameCodecBuilder.forServer()
                        .validateHeaders(true)
                        .validateRequiredPseudoHeaders(true)
                        .initialSettings(new Http2Settings().maxConcurrentStreams(Config.getHttp2MaxConcurrentStreams()))
                        .build(),
                new Http2MultiplexHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(
                                new ChunkedWriteHandler(),
                                new Http2RequestTimeoutHandler(Config.getHttp2RequestTimeoutSeconds(), TimeUnit.SECONDS),
                                http2RouterHandler
                        );
                    }
                })
        );
    }

    public static class Builder {

        private boolean useSsl;
        private SslContext sslContext;
        private boolean useHttp1;
        private boolean useHttp2;
        private Cors cors;
        private boolean corsEnabled;
        private final Http1RouterHandler http1RouterHandler = new Http1RouterHandler();
        private final Http2RouterHandler http2RouterHandler = new Http2RouterHandler();
        private Path certOrChainFilePath;
        private Path privKeyFilePath;

        public Builder() {
            this.useSsl = false;
            this.useHttp1 = false;
            this.useHttp2 = false;
            AsciiArt.printLogo(LOGGER::info);
            Config.init();
        }

        public Builder addHttp1Context(final String path, final Http1SleekHandler http1SleekHandler, final Cors cors,
                                       final HttpMethod httpMethod, final HttpMethod... moreMethods) {

            if (this.http1RouterHandler.handlers.containsKey(path)) throw new RuntimeException("ambiguous.");
            useHttp1 = true;
            final Set<HttpMethod> httpMethods = new HashSet<>(Set.of(httpMethod));
            httpMethods.addAll(Set.of(moreMethods));
            this.http1RouterHandler.handlers.put(path, new Http1Setup(http1SleekHandler, httpMethods, cors));
            return this;
        }

        public Builder addHttp1Context(final String path, final Http1SleekHandler http1SleekHandler,
                                       final HttpMethod httpMethod, final HttpMethod... moreMethods) {

            return addHttp1Context(path, http1SleekHandler, null, httpMethod, moreMethods);
        }

        public Builder serveHttp1Files(final String rootDir, final Http1SleekHandler http1SleekHandler,
                                       final Cors cors, final HttpMethod httpMethod, final HttpMethod... moreMethods) {

            final Path rootPath = Path.of(rootDir).toAbsolutePath().normalize();
            if (this.http1RouterHandler.fileHandlers.containsKey(rootPath)) throw new RuntimeException("ambiguous.");
            this.useHttp1 = true;
            final Set<HttpMethod> httpMethods = new HashSet<>(Set.of(httpMethod));
            httpMethods.addAll(Set.of(moreMethods));
            this.http1RouterHandler.fileHandlers.put(rootPath, new Http1Setup(http1SleekHandler, httpMethods, cors));
            return this;
        }

        public Builder serveHttp1Files(final String rootDir, final Http1SleekHandler http1SleekHandler,
                                       final HttpMethod httpMethod, final HttpMethod... moreMethods) {

            return serveHttp1Files(rootDir, http1SleekHandler, null, httpMethod, moreMethods);
        }

        public Builder addHttp2Context(final String path, final Http2SleekHandler http2SleekHandler, final Cors cors,
                                       final HttpMethod httpMethod, final HttpMethod... moreMethods) {

            if (this.http2RouterHandler.handlers.containsKey(path)) throw new RuntimeException("ambiguous.");
            useHttp2 = true;
            final Set<HttpMethod> httpMethods = new HashSet<>(Set.of(httpMethod));
            httpMethods.addAll(Set.of(moreMethods));
            this.http2RouterHandler.handlers.put(path, new Http2Setup(http2SleekHandler, httpMethods, cors));
            return this;
        }

        public Builder addHttp2Context(final String path, final Http2SleekHandler http2SleekHandler,
                                       final HttpMethod httpMethod, final HttpMethod... moreMethods) {

            return addHttp2Context(path, http2SleekHandler, null, httpMethod, moreMethods);
        }

        public Builder serveHttp2Files(final String rootDir, final Http2SleekHandler http2SleekHandler,
                                       final Cors cors, final HttpMethod httpMethod, final HttpMethod... moreMethods) {

            final Path rootPath = Path.of(rootDir).toAbsolutePath().normalize();
            if (this.http2RouterHandler.fileHandlers.containsKey(rootPath)) throw new RuntimeException("ambiguous.");
            this.useHttp2 = true;
            final Set<HttpMethod> httpMethods = new HashSet<>(Set.of(httpMethod));
            httpMethods.addAll(Set.of(moreMethods));
            this.http2RouterHandler.fileHandlers.put(rootPath, new Http2Setup(http2SleekHandler, httpMethods, cors));
            return this;
        }

        public Builder serveHttp2Files(final String rootDir, final Http2SleekHandler http2SleekHandler,
                                       final HttpMethod httpMethod, final HttpMethod... moreMethods) {

            return serveHttp2Files(rootDir, http2SleekHandler, null, httpMethod, moreMethods);
        }

        public Builder withCors(final Cors cors) {
            this.corsEnabled = true;
            this.cors = cors;
            return this;
        }

        public Builder withSsl(final Path certOrChainFilePath, final Path privKeyFilePath) {
            this.useSsl = true;
            this.certOrChainFilePath = certOrChainFilePath;
            this.privKeyFilePath = privKeyFilePath;
            return this;
        }

        public SleekerServer build() {
            if (useSsl)
                this.sslContext = new ServerSsl().create(certOrChainFilePath, privKeyFilePath, useHttp1, useHttp2);
            return new SleekerServer(this);
        }
    }
}
