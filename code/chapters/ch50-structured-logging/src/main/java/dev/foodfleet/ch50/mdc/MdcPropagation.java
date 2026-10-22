package dev.foodfleet.ch50.mdc;

import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Carries the MDC across a thread hand-off: captured when the task is created, installed while it runs, and the
 * worker's own MDC restored afterwards, so nothing leaks into the next task the worker runs. The OpenTelemetry agent
 * carries trace context across executors, but not the MDC: values put there by application code stay on the thread
 * that put them.
 */
public final class MdcPropagation {

    private MdcPropagation() {
    }

    public static Runnable wrap(Runnable task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            install(captured);
            try {
                task.run();
            } finally {
                install(previous);
            }
        };
    }

    public static <T> Callable<T> wrap(Callable<T> task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            install(captured);
            try {
                return task.call();
            } finally {
                install(previous);
            }
        };
    }

    private static void install(Map<String, String> map) {
        if (map == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(map);
        }
    }
}
