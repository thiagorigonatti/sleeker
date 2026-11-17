/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http1;

public final class Http1RequestAdmissionController {

    private final int maxRequests;
    private final long maxBodyBytes;

    private int activeRequests;
    private long reservedBodyBytes;

    public Http1RequestAdmissionController(
            final int maxRequests,
            final long maxBodyBytes) {

        if (maxRequests <= 0 || maxBodyBytes <= 0) {
            throw new IllegalArgumentException(
                    "Admission limits must be positive"
            );
        }

        this.maxRequests = maxRequests;
        this.maxBodyBytes = maxBodyBytes;
    }

    // Allows a new request without blocking the Event Loop. Returns null when the global limit has been reached.
    public synchronized Permit tryAcquire() {

        if (activeRequests >= maxRequests) {
            return null;
        }

        activeRequests++;

        return new Permit(this);
    }

    private synchronized boolean tryReserveBody(
            final Permit permit,
            final long bytes) {

        if (bytes < 0) {
            throw new IllegalArgumentException(
                    "bytes must be non-negative"
            );
        }

        if (permit.closed) {
            return false;
        }

        if (bytes > maxBodyBytes - reservedBodyBytes) {
            return false;
        }

        reservedBodyBytes += bytes;
        permit.bodyBytes += bytes;

        return true;
    }

    private synchronized void release(final Permit permit) {

        // Prevents double-release.
        if (permit.closed) {
            return;
        }

        permit.closed = true;

        activeRequests--;
        reservedBodyBytes -= permit.bodyBytes;

        permit.bodyBytes = 0;
    }

    public synchronized int activeRequests() {
        return activeRequests;
    }

    public synchronized long reservedBodyBytes() {
        return reservedBodyBytes;
    }

    public synchronized int availableRequests() {
        return maxRequests - activeRequests;
    }

    public static final class Permit implements AutoCloseable {

        private final Http1RequestAdmissionController owner;

        private long bodyBytes;
        private boolean closed;

        private Permit(
                final Http1RequestAdmissionController owner) {

            this.owner = owner;
        }

        public boolean tryReserveBody(final long bytes) {
            return owner.tryReserveBody(this, bytes);
        }

        @Override
        public void close() {
            owner.release(this);
        }
    }
}
