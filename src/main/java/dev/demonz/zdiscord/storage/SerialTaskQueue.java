package dev.demonz.zdiscord.storage;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class SerialTaskQueue {

    private final Consumer<Runnable> scheduler;
    private final Consumer<Throwable> failureHandler;
    private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    private int pending;
    private boolean workerRequested;
    private boolean workerRunning;

    SerialTaskQueue(Consumer<Runnable> scheduler, Consumer<Throwable> failureHandler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
    }

    void execute(Runnable task) {
        Objects.requireNonNull(task, "task");
        boolean scheduleWorker;
        synchronized (this) {
            tasks.addLast(task);
            pending++;
            scheduleWorker = !workerRequested;
            workerRequested = true;
        }
        if (scheduleWorker) {
            try {
                scheduler.accept(this::drainSynchronously);
            } catch (Throwable failure) {
                reportFailure(failure);

                drainSynchronously();
            }
        }
    }

    synchronized int pendingCount() {
        return pending;
    }

    void drainSynchronously() {
        synchronized (this) {
            if (workerRunning) return;
            workerRunning = true;
        }

        while (true) {
            Runnable task;
            synchronized (this) {
                task = tasks.pollFirst();
                if (task == null) {
                    workerRunning = false;
                    workerRequested = false;
                    notifyAll();
                    return;
                }
            }
            try {
                task.run();
            } catch (Throwable failure) {
                reportFailure(failure);
            } finally {
                synchronized (this) {
                    pending--;
                    notifyAll();
                }
            }
        }
    }

    synchronized boolean awaitIdle(long timeoutMillis) throws InterruptedException {
        long remaining = TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
        long deadline = System.nanoTime() + remaining;
        while (pending > 0 && remaining > 0L) {
            TimeUnit.NANOSECONDS.timedWait(this, remaining);
            remaining = deadline - System.nanoTime();
        }
        return pending == 0;
    }

    private void reportFailure(Throwable failure) {
        try {
            failureHandler.accept(failure);
        } catch (Throwable ignored) {

        }
    }
}
