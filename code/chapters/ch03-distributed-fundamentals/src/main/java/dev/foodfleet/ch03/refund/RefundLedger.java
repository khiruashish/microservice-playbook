package dev.foodfleet.ch03.refund;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * Stand-in for the payment gateway's refund API: the side effect we must not perform twice.
 * Every call here moves real money back to a customer's UPI account or card.
 */
@Component
public class RefundLedger {

    private final List<Refund> issued = new CopyOnWriteArrayList<>();

    public Refund issue(String orderId, long amountPaise) {
        Refund refund = new Refund(UUID.randomUUID(), orderId, amountPaise);
        issued.add(refund);
        return refund;
    }

    public List<Refund> issued() {
        return List.copyOf(issued);
    }

    public record Refund(UUID refundId, String orderId, long amountPaise) {
    }
}
