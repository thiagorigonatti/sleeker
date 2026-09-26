/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;

import io.netty.handler.stream.ChunkedWriteHandler;
import me.thiagorigonatti.sleeker.core.http1.Http1RouterHandler;
import me.thiagorigonatti.sleeker.core.http1.Http1Setup;
import me.thiagorigonatti.sleeker.core.http1.Http1SleekHandler;
import me.thiagorigonatti.sleeker.core.http2.Http2RouterHandler;
import me.thiagorigonatti.sleeker.core.http2.Http2Setup;
import me.thiagorigonatti.sleeker.core.http2.Http2SleekHandler;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SleekerServer {

    private static final Logger LOGGER = LogManager.getLogger(SleekerServer.class);

    public static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    private final boolean useSsl;
    private final SslContext sslContext;

    private final boolean useHttp1;
    private final boolean useHttp2;

    private final Http1RouterHandler http1RouterHandler;
    private final Http2RouterHandler http2RouterHandler;

    protected SleekerServer(final Builder builder) {
        this.useSsl = builder.useSsl;
        this.sslContext = builder.sslContext;
        this.useHttp1 = builder.useHttp1;
        this.useHttp2 = builder.useHttp2;


        if (!useSsl && useHttp2) {
            throw new AssertionError("HTTP2 protocol requires SSL, which was not enabled, check whether SSL context was corrected created, or disable HTTP2 contexts.");
        }

        if (builder.corsEnabled) {
            builder.http1RouterHandler.handlers.forEach((s, http1Setup) ->
                    http1Setup.httpMethodList().add(HttpMethod.OPTIONS));
            builder.http1RouterHandler.setCorsEnabled(true);
            builder.http1RouterHandler.setCors(builder.cors);

            builder.http2RouterHandler.handlers.forEach((s, http2Setup) ->
                    http2Setup.httpMethodList().add(HttpMethod.OPTIONS));
            builder.http2RouterHandler.setCorsEnabled(true);
            builder.http2RouterHandler.setCors(builder.cors);
        }

        this.http1RouterHandler = builder.http1RouterHandler;
        this.http2RouterHandler = builder.http2RouterHandler;
    }


    public void startServer(final SocketAddress socketAddress, final ServerIo serverIo) throws Exception {

        final SleekIo sleekIo = Config.getSleekIo(serverIo,
                (socketAddress instanceof UnixDomainSocketAddress || socketAddress instanceof DomainSocketAddress));

        IoHandlerFactory ioHandlerFactory = sleekIo.getIoHandlerFactory();

        EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, ioHandlerFactory);
        EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(1, ioHandlerFactory);

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
                                            case ApplicationProtocolNames.HTTP_2 -> configureHttp2(ctx.pipeline());
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
        pipeline.addLast(new HttpServerCodec(), new HttpObjectAggregator(65536), http1RouterHandler);
    }

    private void configureHttp1Chunked(final ChannelPipeline pipeline) {
        pipeline.addLast(new HttpServerCodec(), new HttpObjectAggregator(65536), new ChunkedWriteHandler(), http1RouterHandler);
    }

    private void configureHttp2(final ChannelPipeline pipeline) {
        pipeline.addLast(Http2FrameCodecBuilder.forServer().build(), new Http2MultiplexHandler(http2RouterHandler));
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

        public Builder() {
            this.useSsl = false;
            this.useHttp1 = false;
            this.useHttp2 = false;
            AsciiArt.printLogo(LOGGER::info);
            Config.init();
        }

        public Builder withSsl(final Path certOrChainFilePath, final Path privKeyFilePath) throws Exception {
            this.useSsl = true;
            this.sslContext = new ServerSsl().create(certOrChainFilePath, privKeyFilePath);
            return this;
        }

        public Builder addHttp1Context(final String path, final Http1SleekHandler http1SleekHandler, final HttpMethod... allowedHttpMethods) {
            useHttp1 = true;
            final Set<HttpMethod> httpMethods = new HashSet<>(Set.of(allowedHttpMethods));
            this.http1RouterHandler.handlers.put(path, new Http1Setup(http1SleekHandler, httpMethods));
            return this;
        }

        public Builder serveHttp1Files(final String rootDir, final Http1SleekHandler http1SleekHandler, final HttpMethod... allowedHttpMethods) {
            useHttp1 = true;
            final Set<HttpMethod> httpMethods = new HashSet<>(Set.of(allowedHttpMethods));
            this.http1RouterHandler.fileHandlers.put(rootDir, new Http1Setup(http1SleekHandler, httpMethods));
            return this;
        }

        public Builder addHttp2Context(final String path, final Http2SleekHandler http2SleekHandler, final HttpMethod... allowedHttpMethods) {
            useHttp2 = true;
            final Set<HttpMethod> httpMethods = new HashSet<>(Set.of(allowedHttpMethods));
            this.http2RouterHandler.handlers.put(path, new Http2Setup(http2SleekHandler, httpMethods));
            return this;
        }

        public Builder withCors(Cors cors) {
            this.corsEnabled = true;
            this.cors = cors;
            return this;
        }

        public SleekerServer build() {
            return new SleekerServer(this);
        }
    }
}
