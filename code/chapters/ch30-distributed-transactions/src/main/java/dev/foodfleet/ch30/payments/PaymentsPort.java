package dev.foodfleet.ch30.payments;

import java.util.List;

/**
 * How order-service reaches payments. Must be idempotent: a batch can be delivered again after a crash, a
 * timeout or a lost reply. Throws if payments can't be reached or can't decide now; nothing is lost, the batch
 * is retried.
 */
public interface PaymentsPort {

    List<Decision> apply(List<OrderPlaced> batch);
}
