/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.exception;

import io.netty.handler.codec.http.HttpResponseStatus;
import me.thiagorigonatti.sleeker.util.ContentType;

public class HttpSleekException extends RuntimeException {

    private final HttpResponseStatus httpResponseStatus;
    private final String responseMessage;
    private final ContentType contentType;

    protected HttpSleekException(BaseBuilder<?> builder) {
        super(builder.responseMessage);
        this.httpResponseStatus = builder.httpResponseStatus;
        this.responseMessage = builder.responseMessage;
        this.contentType = builder.contentType;
    }

    public HttpResponseStatus getHttpResponseStatus() {
        return httpResponseStatus;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public ContentType getContentType() {
        return contentType;
    }

    public static class BaseBuilder<T extends BaseBuilder<T>> {
        private HttpResponseStatus httpResponseStatus;
        private String responseMessage;
        private ContentType contentType;

        public T httpResponseStatus(HttpResponseStatus httpResponseStatus) {
            this.httpResponseStatus = httpResponseStatus;
            return self();
        }

        public T responseMessage(String responseMessage) {
            this.responseMessage = responseMessage;
            return self();
        }

        public T contentType(ContentType contentType) {
            this.contentType = contentType;
            return self();
        }

        @SuppressWarnings("unchecked")
        protected T self() {
            return (T) this;
        }

        public HttpSleekException build() {
            return new HttpSleekException(this);
        }
    }
}
