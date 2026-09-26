/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.tls;

import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.*;
import me.thiagorigonatti.sleeker.core.Config;

import java.nio.file.Path;

public class ServerSsl {

    public SslContext create(Path certOrChainFilePath, Path privKeyFilePath) throws Exception {

        final String[] applicationProtocolNames = Config.isHttp2Priority()
                ? new String[]{ApplicationProtocolNames.HTTP_2, ApplicationProtocolNames.HTTP_1_1}
                : new String[]{ApplicationProtocolNames.HTTP_1_1, ApplicationProtocolNames.HTTP_2};

        return SslContextBuilder.forServer(certOrChainFilePath.toFile(), privKeyFilePath.toFile())
                .sslProvider(SslProvider.OPENSSL)
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        applicationProtocolNames))
                .build();
    }
}
