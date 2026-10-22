package dev.foodfleet.ch49.propagation;

import io.opentelemetry.context.Context;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Thread hand-offs keep the trace only if something carries the context across. The Java agent wraps the JDK's
 * executors for you; code that hands work over any other way (its own queue and worker thread, a library's
 * callback thread, a scheduled job) must capture {@link Context#current()} when the work is created and make it
 * current when the work runs. {@link Context#wrap(Runnable)} does both.
 */
public final class TracedExecutors {

    private TracedExecutors() {
    }

    /** Every task runs in the context that submitted it, with or without the agent. */
    public static ExecutorService wrap(ExecutorService delegate) {
        return Context.taskWrapping(delegate);
    }

    public static ScheduledExecutorService wrap(ScheduledExecutorService delegate) {
        return Context.taskWrapping(delegate);
    }

    /** Virtual threads, one per task, each in the submitter's context. */
    public static ExecutorService virtualThreads() {
        return Context.taskWrapping(Executors.newVirtualThreadPerTaskExecutor());
    }

    /** For a hand-rolled queue: put the wrapped task, not the bare one. */
    public static void enqueue(BlockingQueue<Runnable> queue, Runnable task) throws InterruptedException {
        queue.put(Context.current().wrap(task));
    }
}
