/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.io;

import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;

public class EpollIo implements SleekIo {

    private final boolean unixDomainSocket;
    private final int maxEvents;

    public EpollIo(int maxEvents, boolean unixDomainSocket) throws Exception {
        if (!Epoll.isAvailable()) throw new Exception(Epoll.unavailabilityCause());
        this.maxEvents = maxEvents;
        this.unixDomainSocket = unixDomainSocket;
    }

    @Override
    public Class<? extends ServerChannel> getServerChannelClass() {
        return unixDomainSocket ? EpollServerDomainSocketChannel.class : EpollServerSocketChannel.class;
    }

    @Override
    public IoHandlerFactory getIoHandlerFactory() {
        return EpollIoHandler.newFactory(this.maxEvents, DefaultSelectStrategyFactory.INSTANCE);
    }

    @Override
    public boolean isAvailable() {
        return Epoll.isAvailable();
    }
}
