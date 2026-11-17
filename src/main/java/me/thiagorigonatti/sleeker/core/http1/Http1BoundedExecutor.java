/*
 * Copyright (c) 2026. This project is fully authored by Thiago Rigonatti (https://github.com/thiagorigonatti)
 * and is available under Apache License Version 2.0, January 2004 http://www.apache.org/licenses/
 */

package me.thiagorigonatti.sleeker.core.http1;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public final class Http1BoundedExecutor implements Executor {

    private static final Logger LOGGER = LogManager.getLogger(Http1BoundedExecutor.class);

    private record Pending(Runnable command, Runnable onRejected) {
    }

    private final Executor delegate;
    private final int maxConcurrent;
    private final int maxQueued;

    private final ArrayDeque<Pending> queue = new ArrayDeque<>();

    private int active;

    public Http1BoundedExecutor(final Executor delegate, final int maxConcurrent, final int maxQueued) {

        if (maxConcurrent <= 0 || maxQueued <= 0) {
            throw new IllegalArgumentException("Executor limits must be positive");
        }

        this.delegate = Objects.requireNonNull(delegate);
        this.maxConcurrent = maxConcurrent;
        this.maxQueued = maxQueued;
    }

    @Override
    public void execute(final Runnable command) {
        execute(command, () -> {
        });
    }

    public void execute(final Runnable command, final Runnable onRejected) {

        final Pending pending = new Pending(Objects.requireNonNull(command), Objects.requireNonNull(onRejected));

        synchronized (this) {

            // All execution slots are occupied.
            // Queue the task without blocking the Netty EventLoop.
            if (active >= maxConcurrent) {

                if (queue.size() >= maxQueued) {
                    throw new RejectedExecutionException("HTTP/1 global execution queue is full");
                }

                queue.addLast(pending);
                return;
            }

            // Reserve one execution slot.
            active++;
        }

        dispatch(pending);
    }

    private void dispatch(final Pending initial) {

        Pending current = initial;

        while (true) {

            final Pending next = current;

            try {
                delegate.execute(() -> {
                    try {
                        next.command().run();
                    } finally {
                        finished();
                    }
                });

                return;

            } catch (final RejectedExecutionException rejected) {

                // The underlying executor rejected the task,
                // for example because it is shutting down.
                // Notify its owner so it can release resources.
                try {
                    next.onRejected().run();
                } catch (final RuntimeException callbackFailure) {
                    LOGGER.error("HTTP/1 rejection cleanup failed", callbackFailure);
                }

                // Drain rejected work without recursion.
                synchronized (this) {
                    current = queue.pollFirst();

                    if (current == null) {
                        active--;
                        return;
                    }
                }
            }
        }
    }

    private void finished() {

        final Pending next;

        synchronized (this) {

            // Preserve the slot while handing it to the next task.
            next = queue.pollFirst();

            if (next == null) {
                active--;
                return;
            }
        }

        // Dispatch outside the global lock.
        dispatch(next);
    }

    public synchronized int activeTasks() {
        return active;
    }

    public synchronized int availableSlots() {
        return maxConcurrent - active;
    }

    public synchronized int queuedTasks() {
        return queue.size();
    }
}
