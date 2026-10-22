package dev.foodfleet.lab18;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Expensive work (a tenant's monthly report) runs at most one at a time per tenant. Chapter 75: six concurrent
 * 133 ms reports pushed small tenants' p99 from 3.8-4.8 ms to 25-38 ms; one report per tenant at a time held it at
 * 8.4-10.6 ms and still completed 72-78 reports of 81. A statement timeout alone cancelled all of them.
 */
@Component
public class TenantBulkheads {

    public static final class Busy extends RuntimeException {
        Busy(String tenant) {
            super("a report for " + tenant + " is already running");
        }
    }

    private final Map<String, Semaphore> reports = new ConcurrentHashMap<>();

    public <T> T report(String tenantId, Supplier<T> work) {
        Semaphore permit = reports.computeIfAbsent(tenantId, t -> new Semaphore(1));
        if (!permit.tryAcquire()) {
            throw new Busy(tenantId);                 // 429: try again when the running one finishes
        }
        try {
            return work.get();
        } finally {
            permit.release();
        }
    }
}
