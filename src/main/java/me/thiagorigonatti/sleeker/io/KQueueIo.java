/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.io;

import io.netty.channel.IoHandlerFactory;
import io.netty.channel.ServerChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel;
import io.netty.channel.kqueue.KQueueServerSocketChannel;

public class KQueueIo implements SleekIo {

    private final boolean unixDomainSocket;

    public KQueueIo(boolean unixDomainSocket) throws Exception {
        if (!KQueue.isAvailable()) throw new Exception(KQueue.unavailabilityCause());
        this.unixDomainSocket = unixDomainSocket;
    }

    @Override
    public Class<? extends ServerChannel> getServerChannelClass() {
        return this.unixDomainSocket ? KQueueServerDomainSocketChannel.class : KQueueServerSocketChannel.class;
    }

    @Override
    public IoHandlerFactory getIoHandlerFactory() {
        return KQueueIoHandler.newFactory();
    }

    @Override
    public boolean isAvailable() {
        return KQueue.isAvailable();
    }
}
