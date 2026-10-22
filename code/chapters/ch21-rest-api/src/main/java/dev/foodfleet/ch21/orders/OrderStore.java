package dev.foodfleet.ch21.orders;

import dev.foodfleet.ch21.paging.Cursor;
import java.util.List;
import java.util.Optional;

public interface OrderStore {

    final class OrderNotFound extends RuntimeException {
        public OrderNotFound(String id) {
            super("no order " + id);
        }
    }

    /** Someone else changed the order since the client read it (optimistic concurrency). */
    final class VersionConflict extends RuntimeException {
        private final long currentVersion;

        public VersionConflict(String id, long currentVersion) {
            super("order " + id + " is at version " + currentVersion);
            this.currentVersion = currentVersion;
        }

        public long currentVersion() {
            return currentVersion;
        }
    }

    /** Newest first; the {@code limit} orders strictly after {@code after} in that order. */
    List<OrderSummary> page(String customerId, Optional<Cursor> after, int limit);

    Optional<OrderSummary> find(String orderId);

    /** Updates only if the order is still at {@code expectedVersion}; returns the new state. */
    OrderSummary updateDeliveryInstructions(String orderId, long expectedVersion, String instructions);
}
