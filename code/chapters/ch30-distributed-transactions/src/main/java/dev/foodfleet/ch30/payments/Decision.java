package dev.foodfleet.ch30.payments;

/** Payment-service's answer for one order. The same order always gets the same answer, however often it is sent. */
public record Decision(long orderId, boolean debited, String reason) {

    public static Decision debited(long orderId) {
        return new Decision(orderId, true, null);
    }

    public static Decision rejected(long orderId, String reason) {
        return new Decision(orderId, false, reason);
    }
}
