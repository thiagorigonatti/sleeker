/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class Http2HeaderTimeoutRawHandler extends ChannelInboundHandlerAdapter {

    /*
     * Client connection preface:
     *
     * PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n
     */
    private static final int CONNECTION_PREFACE_LENGTH = 24;

    /*
     * HTTP/2 frame header:
     *
     * Length    3 bytes
     * Type      1 byte
     * Flags     1 byte
     * Stream ID 4 bytes
     */
    private static final int FRAME_HEADER_LENGTH = 9;

    private static final int FRAME_TYPE_HEADERS = 0x1;
    private static final int FRAME_TYPE_CONTINUATION = 0x9;

    private static final int FLAG_END_HEADERS = 0x4;

    private final long timeoutNanos;


    // We just inspect the bytes.
    // We do not change readerIndex() from the original ByteBuf.
    private final byte[] frameHeader = new byte[FRAME_HEADER_LENGTH];

    private int prefaceRemaining = CONNECTION_PREFACE_LENGTH;

    private int frameHeaderBytes;
    private int framePayloadRemaining;

    private int frameType = -1;
    private int frameFlags;
    private int frameStreamId;

    /*
     * An HTTP/2 header block can be:
     *
     * HEADERS
     *
     * or:
     *
     * HEADERS
     * CONTINUATION
     * CONTINUATION
     *...
     */
    private boolean headerBlockOpen;
    private int headerBlockStreamId = -1;


    // Absolute deadline. It is not updated when more bytes arrive.

    private boolean deadlineActive;
    private long deadlineStartedNanos;

    private ScheduledFuture<?> timeoutFuture;

    public Http2HeaderTimeoutRawHandler(final long timeout, final TimeUnit unit) {

        if (timeout <= 0) {
            throw new IllegalArgumentException("timeout must be greater than zero");
        }

        this.timeoutNanos = unit.toNanos(timeout);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {

        if (msg instanceof final ByteBuf buffer && buffer.isReadable()) {

            inspect(buffer);

            scheduleTimeoutIfNecessary(ctx);
        }

        ctx.fireChannelRead(msg);
    }

    private void inspect(final ByteBuf buffer) {

        int index = buffer.readerIndex();
        final int writerIndex = buffer.writerIndex();

        /*
         * --------------------------------------------------------
         * HTTP/2 client connection preface
         * --------------------------------------------------------
         *
         * Http2FrameCodec will also validate this.
         * We just need to ignore these 24 bytes so that our parser starts exactly at the first frame.
         */
        if (this.prefaceRemaining > 0) {

            final int skipped = Math.min(this.prefaceRemaining, writerIndex - index);

            this.prefaceRemaining -= skipped;
            index += skipped;

            if (index == writerIndex) {
                return;
            }
        }

        /*
         * --------------------------------------------------------
         * Frames HTTP/2
         * --------------------------------------------------------
         */
        while (index < writerIndex) {

            /*
             * We are still reading the 9-byte frame header.
             */
            if (this.frameHeaderBytes < FRAME_HEADER_LENGTH) {

                /*
                 * First byte of a new frame.
                 *
                 * We still don't know the TYPE because it is the fourth byte.
                 *
                 * We provisionally start the deadline here so that a
                 * client cannot send the first 3 bytes of the frame
                 * infinitely slowly.
                 *
                 * If we find out later that it is not HEADERS, we cancel it.
                 */
                if (this.frameHeaderBytes == 0 && !this.headerBlockOpen) {

                    startDeadline();
                }

                final int bytesToCopy = Math.min(FRAME_HEADER_LENGTH - this.frameHeaderBytes, writerIndex - index);

                final int previousHeaderBytes = this.frameHeaderBytes;

                buffer.getBytes(index, this.frameHeader, this.frameHeaderBytes, bytesToCopy);

                this.frameHeaderBytes += bytesToCopy;
                index += bytesToCopy;

                /*
                 * The TYPE is the fourth byte.
                 *
                 * As soon as it exists we already know if we are
                 * starting a HEADERS or some other frame.
                 */
                if (previousHeaderBytes < 4 && this.frameHeaderBytes >= 4) {

                    this.frameType = this.frameHeader[3] & 0xFF;

                    /*
                     * There was no header block in progress.
                     */
                    if (!this.headerBlockOpen) {

                        if (this.frameType == FRAME_TYPE_HEADERS) {

                            /*
                             * Now we confirm that that deadline
                             * provisional actually belongs to HEADERS.
                             */
                            this.headerBlockOpen = true;

                        } else {

                            /*
                             * It was DATA, SETTINGS, PING, WINDOW_UPDATE,
                             * etc.
                             *
                             * Therefore there is no timeout header.
                             */
                            stopDeadline();
                        }
                    }
                }

                /*
                 * Frame header still incomplete.
                 */
                if (this.frameHeaderBytes < FRAME_HEADER_LENGTH) {

                    break;
                }

                parseFrameHeader();

                /*
                 * Frame without payload.
                 */
                if (this.framePayloadRemaining == 0) {
                    frameCompleted();
                }

                continue;
            }

            /*
             * -------------------------------------------------------
             * Frame payload
             * -------------------------------------------------------
             *
             * We do not copy the payload.
             *
             * We just need to know when all the bytes arrived.
             */
            final int consumed = Math.min(this.framePayloadRemaining, writerIndex - index);

            index += consumed;

            this.framePayloadRemaining -= consumed;

            if (this.framePayloadRemaining == 0) {
                frameCompleted();
            }
        }
    }

    private void parseFrameHeader() {

        /*
         * 24-bit unsigned payload length.
         */
        this.framePayloadRemaining = ((this.frameHeader[0] & 0xFF) << 16) | ((this.frameHeader[1] & 0xFF) << 8) | (this.frameHeader[2] & 0xFF);

        this.frameType = this.frameHeader[3] & 0xFF;

        this.frameFlags = this.frameHeader[4] & 0xFF;

        /*
         * First bit is reserved.
         * Stream ID is only 31 bits long.
         */
        this.frameStreamId = ((this.frameHeader[5] & 0x7F) << 24) | ((this.frameHeader[6] & 0xFF) << 16) | ((this.frameHeader[7] & 0xFF) << 8) | (this.frameHeader[8] & 0xFF);

        /*
         * Only now do we have the full stream id of the initial HEADERS.
         */
        if (this.frameType == FRAME_TYPE_HEADERS && this.headerBlockOpen && this.headerBlockStreamId == -1) {

            this.headerBlockStreamId = this.frameStreamId;
        }
    }

    private void frameCompleted() {

        /*
         * END_HEADERS means that this frame ends the header block.
         *
         * Can happen directly in HEADERS:
         *
         * HEADERS + END_HEADERS
         *
         * or in the last CONTINUATION:
         *
         *HEADERS
         * CONTINUATION
         * CONTINUATION + END_HEADERS
         */
        if (this.headerBlockOpen && this.frameStreamId == this.headerBlockStreamId && (this.frameType == FRAME_TYPE_HEADERS || this.frameType == FRAME_TYPE_CONTINUATION) && (this.frameFlags & FLAG_END_HEADERS) != 0) {

            this.headerBlockOpen = false;
            this.headerBlockStreamId = -1;

            stopDeadline();
        }

        /*
         * Prepare reading of the next frame header.
         */
        this.frameHeaderBytes = 0;
        this.framePayloadRemaining = 0;

        this.frameType = -1;
        this.frameFlags = 0;
        this.frameStreamId = 0;
    }

    private void startDeadline() {

        if (this.deadlineActive) {
            return;
        }

        this.deadlineActive = true;
        this.deadlineStartedNanos = System.nanoTime();
    }

    private void stopDeadline() {

        this.deadlineActive = false;
        this.deadlineStartedNanos = 0;

        cancelTimeout();
    }

    private void scheduleTimeoutIfNecessary(final ChannelHandlerContext ctx) {

        if (!this.deadlineActive || this.timeoutFuture != null) {

            return;
        }

        final long elapsed = System.nanoTime() - this.deadlineStartedNanos;

        final long remaining = Math.max(0L, this.timeoutNanos - elapsed);

        this.timeoutFuture = ctx.executor().schedule(() -> {

            this.timeoutFuture = null;

            if (!this.deadlineActive) {
                return;
            }

            /*
             * We are still waiting for the end of
             * header block.
             *
             * At this point the Http2FrameCodec may be
             * in the middle of a HEADERS/CONTINUATION.
             *
             * The safest thing is to close the connection.
             */
            ctx.close();

        }, remaining, TimeUnit.NANOSECONDS);
    }

    private void cancelTimeout() {

        if (this.timeoutFuture == null) {
            return;
        }

        this.timeoutFuture.cancel(false);
        this.timeoutFuture = null;
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {

        cancelTimeout();

        super.channelInactive(ctx);
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) throws Exception {

        cancelTimeout();

        super.handlerRemoved(ctx);
    }
}