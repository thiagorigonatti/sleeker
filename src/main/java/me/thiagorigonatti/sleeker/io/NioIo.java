/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.io;

import io.netty.channel.IoHandlerFactory;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerDomainSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class NioIo implements SleekIo {

    private final boolean unixDomainSocket;

    public NioIo(boolean unixDomainSocket) {
        this.unixDomainSocket = unixDomainSocket;
    }

    @Override
    public Class<? extends ServerChannel> getServerChannelClass() {
        return this.unixDomainSocket ? NioServerDomainSocketChannel.class : NioServerSocketChannel.class;
    }

    @Override
    public IoHandlerFactory getIoHandlerFactory() {
        return NioIoHandler.newFactory();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
