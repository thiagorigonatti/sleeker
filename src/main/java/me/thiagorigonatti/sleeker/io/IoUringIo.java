/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.io;

import io.netty.channel.IoHandlerFactory;
import io.netty.channel.ServerChannel;
import io.netty.channel.uring.*;

public class IoUringIo implements SleekIo {

    private final IoUringIoHandlerConfig config;
    private final boolean unixDomainSocket;

    public IoUringIo(IoUringIoHandlerConfig config, boolean unixDomainSocket) throws Exception {
        if (!IoUring.isAvailable()) throw new Exception(IoUring.unavailabilityCause());
        this.config = config;
        this.unixDomainSocket = unixDomainSocket;
    }

    @Override
    public Class<? extends ServerChannel> getServerChannelClass() {
        return unixDomainSocket ? IoUringServerDomainSocketChannel.class : IoUringServerSocketChannel.class;
    }

    @Override
    public IoHandlerFactory getIoHandlerFactory() {
        return IoUringIoHandler.newFactory(this.config);
    }

    @Override
    public boolean isAvailable() {
        return IoUring.isAvailable();
    }
}
