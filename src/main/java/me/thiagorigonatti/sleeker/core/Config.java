/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core;

import io.netty.channel.uring.IoUringIoHandlerConfig;
import io.netty.util.ResourceLeakDetector;
import me.thiagorigonatti.sleeker.config.Yml;
import me.thiagorigonatti.sleeker.io.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Config {

    private static final Logger LOGGER = LogManager.getLogger(Config.class);

    public static final ExecutorService EXECUTOR_SERVICE = Executors.newVirtualThreadPerTaskExecutor();

    private static final String DEFAULT_CONFIG = "/sleeker-default.yml";
    private static final String USER_CONFIG = "sleeker.yml";

    private static Map<String, Object> defaultSleekerYml;
    private static Map<String, Object> sleekerYml;

    private static boolean http2Priority;

    private static int bossThreads;
    private static int workerThreads;

    private static int ioUringRingSize;
    private static int ioUringCqSize;
    private static int ioUringMaxBoundedWorker;
    private static int ioUringMaxUnBoundedWorker;

    private static int epollMaxEvents;

    private static int http1MaxRequests;
    private static int http1ChunkSize;
    private static int http1MaxPipelineDepth;
    private static int http1HeaderTimeoutSeconds;
    private static int http1RequestTimeoutSeconds;
    private static int http1ConnectionIdleTimeoutSeconds;
    private static int http1MaxContentLength;
    private static int http1MaxConcurrentRequests;
    private static int http1ResponseIdleTimeoutSeconds;
    private static int http1MaxConnections;
    private static int http1SerialMaxPending;
    private static int http1SerialPauseAt;
    private static int http1SerialResumeAt;

    private static int http2ChunkSize;
    private static int http2HeaderTimeoutSeconds;
    private static int http2RequestTimeoutSeconds;
    private static int http2MaxConcurrentStreams;

    private Config() {
        throw new AssertionError("Instantiation of an utility class");
    }

    public static Map<String, Object> getDefaultSleekerYml() {
        return defaultSleekerYml;
    }

    public static Map<String, Object> getSleekerYml() {
        return sleekerYml;
    }

    public static boolean isHttp2Priority() {
        return http2Priority;
    }

    public static int getBossThreads() {
        return bossThreads;
    }

    public static int getWorkerThreads() {
        return workerThreads;
    }

    public static int getHttp1ChunkSize() {
        return http1ChunkSize;
    }

    public static int getHttp2ChunkSize() {
        return http2ChunkSize;
    }

    public static int getIoUringRingSize() {
        return ioUringRingSize;
    }

    public static int getIoUringCqSize() {
        return ioUringCqSize;
    }

    public static int getIoUringMaxBoundedWorker() {
        return ioUringMaxBoundedWorker;
    }

    public static int getIoUringMaxUnBoundedWorker() {
        return ioUringMaxUnBoundedWorker;
    }

    public static int getEpollMaxEvents() {
        return epollMaxEvents;
    }

    public static int getHttp1HeaderTimeoutSeconds() {
        return http1HeaderTimeoutSeconds;
    }

    public static int getHttp1RequestTimeoutSeconds() {
        return http1RequestTimeoutSeconds;
    }

    public static int getHttp1ConnectionIdleTimeoutSeconds() {
        return http1ConnectionIdleTimeoutSeconds;
    }

    public static int getHttp1MaxContentLength() {
        return http1MaxContentLength;
    }

    public static int getHttp1MaxRequests() {
        return http1MaxRequests;
    }

    public static int getHttp1MaxConcurrentRequests() {
        return http1MaxConcurrentRequests;
    }

    public static int getHttp1MaxPipelineDepth() {
        return http1MaxPipelineDepth;
    }

    public static int getHttp1ResponseIdleTimeoutSeconds() {
        return http1ResponseIdleTimeoutSeconds;
    }

    public static int getHttp1MaxConnections() {
        return http1MaxConnections;
    }

    public static int getHttp1SerialMaxPending() {
        return http1SerialMaxPending;
    }

    public static int getHttp1SerialPauseAt() {
        return http1SerialPauseAt;
    }

    public static int getHttp1SerialResumeAt() {
        return http1SerialResumeAt;
    }

    public static int getHttp2HeaderTimeoutSeconds() {
        return http2HeaderTimeoutSeconds;
    }

    public static int getHttp2RequestTimeoutSeconds() {
        return http2RequestTimeoutSeconds;
    }

    public static int getHttp2MaxConcurrentStreams() {
        return http2MaxConcurrentStreams;
    }

    public static void init() {
        try (final InputStream defaultStream = Config.class.getResourceAsStream(DEFAULT_CONFIG)) {
            if (defaultStream == null)
                throw new IllegalStateException("Internal Sleeker configuration " + DEFAULT_CONFIG + " was not found.");
            defaultSleekerYml = new Yml().read(defaultStream);
            if (defaultSleekerYml == null)
                throw new IllegalStateException("Internal Sleeker configuration " + DEFAULT_CONFIG + " could not be parsed.");
        } catch (final IOException exception) {
            throw new UncheckedIOException("Could not read internal Sleeker configuration.", exception);
        }

        sleekerYml = new HashMap<>(defaultSleekerYml);

        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try (final InputStream userStream = classLoader.getResourceAsStream(USER_CONFIG)) {

            if (userStream != null) {
                final Map<String, Object> userSleekerYml = new Yml().read(userStream);
                if (userSleekerYml != null) sleekerYml.putAll(userSleekerYml);
            }

        } catch (final IOException exception) {
            throw new UncheckedIOException("Could not read application Sleeker configuration.", exception);
        }

        http2Priority = (boolean) sleekerYml.get("sleeker.server.http2Priority");
        bossThreads = (int) sleekerYml.get("sleeker.server.bossThreads");
        workerThreads = (int) sleekerYml.get("sleeker.server.workerThreads");

        ioUringRingSize = (int) sleekerYml.get("sleeker.server.io.ioUring.ringSize");
        ioUringCqSize = (int) sleekerYml.get("sleeker.server.io.ioUring.cqSize");
        ioUringMaxBoundedWorker = (int) sleekerYml.get("sleeker.server.io.ioUring.maxBoundedWorker");
        ioUringMaxUnBoundedWorker = (int) sleekerYml.get("sleeker.server.io.ioUring.maxUnboundedWorker");
        epollMaxEvents = (int) sleekerYml.get("sleeker.server.io.epoll.maxEvents");

        //HTTP-1.1 CONFIG
        http1MaxRequests = (int) sleekerYml.get("sleeker.server.http1.maxRequests");
        http1ConnectionIdleTimeoutSeconds = (int) sleekerYml.get("sleeker.server.http1.connectionIdleTimeoutSeconds");
        http1ResponseIdleTimeoutSeconds = (int) sleekerYml.get("sleeker.server.http1.responseIdleTimeoutSeconds");
        http1HeaderTimeoutSeconds = (int) sleekerYml.get("sleeker.server.http1.headerTimeoutSeconds");
        http1RequestTimeoutSeconds = (int) sleekerYml.get("sleeker.server.http1.requestTimeoutSeconds");
        http1MaxContentLength = (int) sleekerYml.get("sleeker.server.http1.maxContentLength");
        http1ChunkSize = (int) sleekerYml.get("sleeker.server.http1.chunkSize");
        http1MaxConnections = (int) sleekerYml.get("sleeker.server.http1.maxConnections");
        http1MaxPipelineDepth = (int) sleekerYml.get("sleeker.server.http1.maxPipelineDepth");
        http1MaxConcurrentRequests = (int) sleekerYml.get("sleeker.server.http1.maxConcurrentRequests");
        http1SerialMaxPending = (int) sleekerYml.get("sleeker.server.http1.serialMaxPending");
        http1SerialPauseAt = (int) sleekerYml.get("sleeker.server.http1.serialPauseAt");
        http1SerialResumeAt = (int) sleekerYml.get("sleeker.server.http1.serialResumeAt");

        //HTTP-2 CONFIG
        http2HeaderTimeoutSeconds = (int) sleekerYml.get("sleeker.server.http2.headerTimeoutSeconds");
        http2RequestTimeoutSeconds = (int) sleekerYml.get("sleeker.server.http2.requestTimeoutSeconds");
        http2MaxConcurrentStreams = (int) sleekerYml.get("sleeker.server.http2.maxConcurrentStreams");
        http2ChunkSize = (int) sleekerYml.get("sleeker.server.http2.chunkSize");
    }

    public static SleekIo getSleekIo(final ServerIo serverIo, final boolean unixDomainSocket) throws Exception {

        LOGGER.debug("UnixDomainSocket = {}", unixDomainSocket);
        LOGGER.debug("Leak Detection Level: {}", ResourceLeakDetector.getLevel());

        switch (serverIo) {

            case TYPE_IOURING -> {
                final IoUringIoHandlerConfig handlerConfig = new IoUringIoHandlerConfig();
                handlerConfig.setRingSize(getIoUringRingSize()).setCqSize(getIoUringCqSize()).setMaxBoundedWorker(getIoUringMaxBoundedWorker()).setMaxUnboundedWorker(getIoUringMaxUnBoundedWorker());

                LOGGER.debug("I/O = IoUring");
                return new IoUringIo(handlerConfig, unixDomainSocket);
            }

            case TYPE_EPOLL -> {
                LOGGER.debug("I/O = Epoll");
                return new EpollIo(getEpollMaxEvents(), unixDomainSocket);
            }

            case TYPE_KQUEUE -> {
                LOGGER.debug("I/O = KQueue");
                return new KQueueIo(unixDomainSocket);
            }

            case TYPE_NIO -> {
                LOGGER.debug("I/O = Nio");
                return new NioIo(unixDomainSocket);
            }

            case TYPE_LOCAL -> {
                LOGGER.debug("I/O = Local");
                return new LocalIo(unixDomainSocket);
            }
        }

        throw new AssertionError("Unknown I/O type");
    }
}
