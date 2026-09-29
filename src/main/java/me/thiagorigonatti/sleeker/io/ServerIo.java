/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.io;

public enum ServerIo {

    TYPE_EPOLL,
    TYPE_IOURING,
    TYPE_KQUEUE,
    TYPE_LOCAL,
    TYPE_NIO,
}
