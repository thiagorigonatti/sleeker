/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http2;

public abstract class Http2SleekHandler {

    protected void handleGET(Http2Request http2Request, Http2Response http2Response) throws Exception {
        Http2Responder.replyNotImplemented(http2Request, http2Response);
    }

    protected void handlePOST(Http2Request http2Request, Http2Response http2Response) throws Exception {
        Http2Responder.replyNotImplemented(http2Request, http2Response);
    }

    protected void handlePUT(Http2Request http2Request, Http2Response http2Response) throws Exception {
        Http2Responder.replyNotImplemented(http2Request, http2Response);
    }

    protected void handlePATCH(Http2Request http2Request, Http2Response http2Response) throws Exception {
        Http2Responder.replyNotImplemented(http2Request, http2Response);
    }

    protected void handleDELETE(Http2Request http2Request, Http2Response http2Response) throws Exception {
        Http2Responder.replyNotImplemented(http2Request, http2Response);
    }

    protected void handleHEAD(Http2Request http2Request, Http2Response http2Response) throws Exception {
        Http2Responder.replyNotImplemented(http2Request, http2Response);
    }

    protected void handleOPTIONS(Http2Request http2Request, Http2Response http2Response) throws Exception {
        Http2Responder.replyNotImplemented(http2Request, http2Response);
    }

    protected void handleTRACE(Http2Request http2Request, Http2Response http2Response) throws Exception {
        Http2Responder.replyNotImplemented(http2Request, http2Response);
    }

    protected void handleCONNECT(Http2Request http2Request, Http2Response http2Response) throws Exception {
        Http2Responder.replyNotImplemented(http2Request, http2Response);
    }
}
