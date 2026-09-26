/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.io;

import io.netty.channel.IoHandlerFactory;
import io.netty.channel.ServerChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;

public class LocalIo implements SleekIo {

    public LocalIo(boolean unixDomainSocket) throws Exception {
        if (unixDomainSocket) throw new Exception("Cannot use LocalIo with Unix Domain Socket.");
    }

    @Override
    public Class<? extends ServerChannel> getServerChannelClass() {
        return LocalServerChannel.class;
    }

    @Override
    public IoHandlerFactory getIoHandlerFactory() {
        return LocalIoHandler.newFactory();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
