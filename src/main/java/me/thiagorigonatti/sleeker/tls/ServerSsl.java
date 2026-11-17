/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.tls;

import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.*;
import me.thiagorigonatti.sleeker.core.Config;
import org.jspecify.annotations.NonNull;

import javax.net.ssl.SSLException;
import java.nio.file.Path;

public class ServerSsl {

    public SslContext create(final Path certOrChainFilePath, final Path privKeyFilePath,
                             final boolean useHttp1, final boolean useHttp2) {

        if (!useHttp1 && !useHttp2) {
            throw new IllegalArgumentException("At least one HTTP protocol must be enabled.");
        }

        final String[] applicationProtocolNames = getApplicationProtocolNames(useHttp1, useHttp2);

        try {
            return SslContextBuilder.forServer(certOrChainFilePath.toFile(), privKeyFilePath.toFile())
                    .sslProvider(SslProvider.OPENSSL)
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .applicationProtocolConfig(new ApplicationProtocolConfig(
                            ApplicationProtocolConfig.Protocol.ALPN,
                            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                            applicationProtocolNames))
                    .build();
        } catch (final SSLException e) {
            throw new AssertionError(e);
        }
    }

    private static String[] getApplicationProtocolNames(boolean useHttp1, boolean useHttp2) {
        final String[] applicationProtocolNames;

        if (useHttp1 && useHttp2) {
            applicationProtocolNames = Config.isHttp2Priority()
                    ? new String[]{ApplicationProtocolNames.HTTP_2, ApplicationProtocolNames.HTTP_1_1}
                    : new String[]{ApplicationProtocolNames.HTTP_1_1, ApplicationProtocolNames.HTTP_2};
        } else if (useHttp2) {
            applicationProtocolNames = new String[]{ApplicationProtocolNames.HTTP_2};
        } else {
            applicationProtocolNames = new String[]{ApplicationProtocolNames.HTTP_1_1};
        }
        return applicationProtocolNames;
    }
}
