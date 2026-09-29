/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http1;

public abstract class Http1SleekHandler {

    protected void handleGET(Http1Request http1Request, Http1Response http1Response) throws Exception {
        Http1Responder.replyNotImplemented(http1Request, http1Response);
    }

    protected void handlePOST(Http1Request http1Request, Http1Response http1Response) throws Exception {
        Http1Responder.replyNotImplemented(http1Request, http1Response);
    }

    protected void handlePUT(Http1Request http1Request, Http1Response http1Response) throws Exception {
        Http1Responder.replyNotImplemented(http1Request, http1Response);
    }

    protected void handlePATCH(Http1Request http1Request, Http1Response http1Response) throws Exception {
        Http1Responder.replyNotImplemented(http1Request, http1Response);
    }

    protected void handleDELETE(Http1Request http1Request, Http1Response http1Response) throws Exception {
        Http1Responder.replyNotImplemented(http1Request, http1Response);
    }

    protected void handleHEAD(Http1Request http1Request, Http1Response http1Response) throws Exception {
        Http1Responder.replyNotImplemented(http1Request, http1Response);
    }

    protected void handleOPTIONS(Http1Request http1Request, Http1Response http1Response) throws Exception {
        Http1Responder.replyNotImplemented(http1Request, http1Response);
    }

    protected void handleTRACE(Http1Request http1Request, Http1Response http1Response) throws Exception {
        Http1Responder.replyNotImplemented(http1Request, http1Response);
    }

    protected void handleCONNECT(Http1Request http1Request, Http1Response http1Response) throws Exception {
        Http1Responder.replyNotImplemented(http1Request, http1Response);
    }
}
