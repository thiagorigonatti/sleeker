/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http1;

import io.netty.channel.Channel;
import me.thiagorigonatti.sleeker.core.Config;
import org.jspecify.annotations.NonNull;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public final class Http1SerialExecutor implements Executor {

    private record Task(Runnable command, Runnable onDiscard) {
    }

    /*
     * Absolute limit of requests belonging to this connection.
     *
     * Includes:
     * - the task currently running;
     * - tasks waiting in the queue.
     */
    private static final int MAX_PENDING = Config.getHttp1SerialMaxPending();


    // Start applying backpressure before reaching the absolute limit.
    private static final int PAUSE_AT = Config.getHttp1SerialPauseAt();


    // Only read again when the queue has drained sufficiently.
    // PAUSE_AT > RESUME_AT avoids constantly toggling autoRead.
    private static final int RESUME_AT = Config.getHttp1SerialResumeAt();

    private final Http1BoundedExecutor executor;
    private final Channel channel;
    private final Queue<Task> tasks = new ArrayDeque<>();

    private Task active;
    private Thread runningThread;
    private boolean autoReadDisabled;
    private boolean failed;

    public Http1SerialExecutor(
            final Http1BoundedExecutor executor,
            final Channel channel) {

        this.executor = Objects.requireNonNull(executor);
        this.channel = Objects.requireNonNull(channel);

        this.channel.closeFuture()
                .addListener(ignored -> channelClosed());
    }

    @Override
    public void execute(final @NonNull Runnable command) {
        execute(command, () -> {
        });
    }

    public synchronized void execute(final Runnable command, final Runnable onDiscard) {


        // isActive() also covers the small window in which the Channel is no longer active,
        // but the closeFuture listener was still unable to execute channelClosed().
        if (this.failed || !this.channel.isActive()) {
            throw new RejectedExecutionException("HTTP/1 serial executor is unavailable");
        }

        if (pendingTasks() >= MAX_PENDING) {
            throw new RejectedExecutionException("Too many pending HTTP/1 requests");
        }

        this.tasks.offer(new Task(command, onDiscard));


         // We do not wait to reach MAX_PENDING.
         // We stop reading new requests early.
        if (pendingTasks() >= PAUSE_AT) {
            disableAutoRead();
        }

        if (this.active == null) {
            scheduleNext();
        }
    }

    private void scheduleNext() {

        this.active = this.tasks.poll();


        // If the queue has drained sufficiently, allow Netty to read from the socket again.
        if (pendingTasks() <= RESUME_AT) {
            enableAutoRead();
        }

        if (this.active == null) {
            return;
        }

        final Task next = this.active;

        try {

            this.executor.execute(() -> {
                final Thread current = Thread.currentThread();
                final boolean shouldRun;

                // Registers the thread before executing the handler.
                // If the channel has already closed, the task will not be executed.
                synchronized (this) {
                    shouldRun = !this.failed && this.channel.isActive();
                    if (shouldRun) {
                        this.runningThread = current;
                    }
                }

                try {
                    if (shouldRun && this.channel.isActive() && !current.isInterrupted()) {
                        next.command().run();
                    } else {
                        // The task left the queue but did not execute.
                        discard(next);
                    }
                } finally {
                    synchronized (this) {
                        if (this.runningThread == current) {
                            this.runningThread = null;
                        }
                    }
                    taskFinished();
                }
            });

        } catch (final RejectedExecutionException exception) {
            fail(next);
        }
    }

    private synchronized void taskFinished() {

        this.active = null;

        // If the Channel closed while this task was running, channelClosed() has already marked the executor as failed.
        // Therefore, no other requests will be executed.
        if (!this.failed) {
            scheduleNext();
        }
    }

    private void channelClosed() {
        final Thread toInterrupt;

        synchronized (this) {
            if (this.failed) {
                return;
            }

            this.failed = true;
            toInterrupt = this.runningThread;
            discardPendingTasks();
        }

        // Never wait for the task to finish inside the EventLoop.
        // Interruption is cooperative: it is not equivalent to forcing a stop.
        if (toInterrupt != null) {
            toInterrupt.interrupt();
        }
    }

    private void fail(final Task rejected) {

        this.failed = true;
        this.active = null;


        // This task has already left the queue, but the Executor refused to execute it.
        discard(rejected);


        // No other requests for this connection should be executed.
        discardPendingTasks();


        // We do not need to reactivate autoRead before closing the Channel.
        this.channel.close();
    }

    private void discardPendingTasks() {

        Task pending;

        while ((pending = this.tasks.poll()) != null) {
            discard(pending);
        }
    }

    private void discard(final Task task) {

        try {
            task.onDiscard().run();
        } catch (final RuntimeException ignored) {
            // Cleaning a task cannot prevent cleaning of the others.
        }
    }

    private int pendingTasks() {
        return this.tasks.size() + (this.active != null ? 1 : 0);
    }

    private void disableAutoRead() {

        if (this.autoReadDisabled) {
            return;
        }

        this.autoReadDisabled = true;

        executeOnEventLoop(() -> this.channel.config().setAutoRead(false));
    }

    private void enableAutoRead() {

        if (!this.autoReadDisabled) {
            return;
        }

        this.autoReadDisabled = false;

        executeOnEventLoop(() -> {


            // We must not reactivate reading of a connection that is already being closed or is no longer active.

            if (this.channel.isActive()) {
                this.channel.config().setAutoRead(true);
            }
        });
    }

    @SuppressWarnings("resource")
    private void executeOnEventLoop(final Runnable task) {

        if (this.channel.eventLoop().inEventLoop()) {
            task.run();
            return;
        }

        this.channel.eventLoop().execute(task);
    }
}