/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.exception;

import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2Headers;

public class Http2SleekException extends HttpSleekException {

    private final Http2Headers http2Headers;
    private final Http2FrameStream http2FrameStream;

    private Http2SleekException(Builder builder) {
        super(builder);
        this.http2Headers = builder.http2Headers;
        this.http2FrameStream = builder.http2FrameStream;
    }

    public Http2Headers getHttp2Headers() {
        return http2Headers;
    }

    public Http2FrameStream getHttp2FrameStream() {
        return http2FrameStream;
    }

    public static class Builder extends BaseBuilder<Builder> {

        private final Http2Headers http2Headers;
        private final Http2FrameStream http2FrameStream;

        public Builder(Http2Headers http2Headers, Http2FrameStream http2FrameStream) {
            this.http2Headers = http2Headers;
            this.http2FrameStream = http2FrameStream;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public Http2SleekException build() {
            return new Http2SleekException(this);
        }
    }
}
