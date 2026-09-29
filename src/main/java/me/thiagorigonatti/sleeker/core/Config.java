/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core;


import io.netty.channel.uring.IoUringIoHandlerConfig;
import me.thiagorigonatti.sleeker.config.Yml;
import me.thiagorigonatti.sleeker.io.*;
import me.thiagorigonatti.sleeker.tls.ServerSsl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.net.URL;
import java.util.Map;

public class Config {
    private static final Logger LOGGER = LogManager.getLogger(Config.class);
    private static Map<String, Object> sleekerYml;

    private static boolean http2Priority;
    private static int bossThreads;
    private static int workerThreads;
    private static int http1ChunkSize;
    private static int http2ChunkSize;

    private Config() {
        throw new AssertionError("Instantiation of an utility class");
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

    public static void init() {

        URL url = ServerSsl.class.getClassLoader().getResource("sleeker.yml");
        File file;

        if (url != null && (file = new File(url.getPath())).exists()) {
            sleekerYml = new Yml().read(file);
            http2Priority = (boolean) sleekerYml.get("sleeker.server.http2Priority");
            bossThreads = (int) sleekerYml.get("sleeker.server.bossThreads");
            workerThreads = (int) sleekerYml.get("sleeker.server.workerThreads");
            http1ChunkSize = (int) sleekerYml.get("sleeker.server.http1.chunkSize");
            http2ChunkSize = (int) sleekerYml.get("sleeker.server.http2.chunkSize");
        } else {
            http2Priority = true;
        }
    }

    public static SleekIo getSleekIo(final ServerIo serverIo, final boolean unixDomainSocket) throws Exception {

        LOGGER.info("UnixDomainSocket = {}", unixDomainSocket);

        switch (serverIo) {
            case TYPE_IOURING -> {

                final CharSequence ioUringConfigPrefix = "sleeker.server.io.ioUring.";

                final IoUringIoHandlerConfig handlerConfig = new IoUringIoHandlerConfig();

                final int ringSize = sleekerYml.get(ioUringConfigPrefix + "ringSize") != null
                        ? (int) sleekerYml.get(ioUringConfigPrefix + "ringSize")
                        : handlerConfig.getRingSize();

                final int cqSize = sleekerYml.get(ioUringConfigPrefix + "cqSize") != null
                        ? (int) sleekerYml.get(ioUringConfigPrefix + "cqSize")
                        : handlerConfig.getCqSize();

                final int maxBoundedWorker = sleekerYml.get(ioUringConfigPrefix + "maxBoundedWorker") != null
                        ? (int) sleekerYml.get(ioUringConfigPrefix + "maxBoundedWorker")
                        : handlerConfig.getMaxBoundedWorker();

                final int maxUnboundedWorker = sleekerYml.get(ioUringConfigPrefix + "maxUnboundedWorker") != null
                        ? (int) sleekerYml.get(ioUringConfigPrefix + "maxUnboundedWorker")
                        : handlerConfig.getMaxUnboundedWorker();

                handlerConfig
                        .setRingSize(ringSize)
                        .setMaxBoundedWorker(maxBoundedWorker)
                        .setMaxUnboundedWorker(maxUnboundedWorker)
                        .setCqSize(cqSize);

                LOGGER.info("I/O = IoUring");
                return new IoUringIo(handlerConfig, unixDomainSocket);
            }

            case TYPE_EPOLL -> {
                LOGGER.info("I/O = Epoll");
                final int maxEvents = sleekerYml.get("sleeker.server.io.epoll.maxEvents") != null
                        ? (int) sleekerYml.get("sleeker.server.io.epoll.maxEvents")
                        : 0;

                return new EpollIo(maxEvents, unixDomainSocket);
            }

            case TYPE_KQUEUE -> {
                LOGGER.info("I/O = KQueue");
                return new KQueueIo(unixDomainSocket);
            }

            case TYPE_NIO -> {
                LOGGER.info("I/O = Nio");
                return new NioIo(unixDomainSocket);
            }

            case TYPE_LOCAL -> {
                LOGGER.info("I/O = Local");
                return new LocalIo(unixDomainSocket);
            }
        }

        throw new AssertionError("Unknown I/O type");
    }
}
