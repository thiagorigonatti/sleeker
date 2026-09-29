/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.util;

public enum ContentType {

    TEXT_PLAIN_UTF8("text/plain; charset=utf-8"),
    APPLICATION_JSON_UTF8("application/json; charset=utf-8"),
    TEXT_HTML_UTF8("text/html; charset=UTF-8"),
    TEXT_JS_UTF8("text/javascript; charset=UTF-8"),
    TEXT_CSS_UTF8("text/css; charset=UTF-8"),
    APPLICATION_VND_APPLE_MPEGURL("application/vnd.apple.mpegurl"),
    VIDEO_MP4("video/mp4"),
    VIDEO_ISO_SEGMENT("video/iso.segment"),
    TEXT_PLAIN("text/plain");

    private final CharSequence mimeType;

    public CharSequence getMimeType() {
        return mimeType;
    }

    ContentType(CharSequence contentType) {
        this.mimeType = contentType;
    }

    public static CharSequence byExtension(String extension) {
        return switch (extension) {
            case "html", "htm" -> ContentType.TEXT_HTML_UTF8.getMimeType();
            case "css" -> ContentType.TEXT_CSS_UTF8.getMimeType();
            case "js" -> ContentType.TEXT_JS_UTF8.getMimeType();
            case "m3u8" -> ContentType.APPLICATION_VND_APPLE_MPEGURL.getMimeType();
            case "m4s" -> ContentType.VIDEO_ISO_SEGMENT.getMimeType();
            case "mp4" -> ContentType.VIDEO_MP4.getMimeType();
            default -> ContentType.TEXT_PLAIN.getMimeType();
        };
    }
}
