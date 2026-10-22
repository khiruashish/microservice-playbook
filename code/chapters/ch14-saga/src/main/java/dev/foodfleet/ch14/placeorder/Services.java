package dev.foodfleet.ch14.placeorder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** The five participants of the place-order saga, each owning its own state (chapter 12). */
public final class Services {

    private Services() {
    }

    /** ordering-service: the order itself. PENDING is a semantic lock: other flows treat it as "in progress". */
    public static final class Orders extends Participant {
        public enum Status { PENDING, APPROVED, REJECTED }

        public final Map<String, Status> status = new ConcurrentHashMap<>();

        public Orders(Faults faults) {
            super("ordering", faults, true);
        }

        public void createPending(String sagaId, String orderId) {
            perform("create", sagaId, () -> status.put(orderId, Status.PENDING));
        }

        public void reject(String sagaId, String orderId) {
            compensate("create", "reject", sagaId, () -> status.put(orderId, Status.REJECTED));
        }

        public void approve(String sagaId, String orderId) {
            perform("approve", sagaId, () -> status.put(orderId, Status.APPROVED));
        }
    }

    /** promotions-service: a coupon with a limited number of uses. */
    public static final class Coupons extends Participant {
        public final AtomicInteger remainingUses;

        public Coupons(Faults faults, int uses) {
            super("promotions", faults, true);
            this.remainingUses = new AtomicInteger(uses);
        }

        public void reserve(String sagaId) {
            perform("reserve", sagaId, () -> {
                if (remainingUses.decrementAndGet() < 0) {
                    remainingUses.incrementAndGet();
                    throw new dev.foodfleet.ch14.saga.SagaFailure.BusinessRejection("coupon exhausted");
                }
            });
        }

        public void release(String sagaId) {
            compensate("reserve", "release", sagaId, remainingUses::incrementAndGet);
        }
    }

    /** payment-service: authorize, void, capture. {@code idempotent=false} reproduces a classic double-charge bug. */
    public static final class Payments extends Participant {
        public final AtomicInteger authorizations = new AtomicInteger();
        public final AtomicInteger voids = new AtomicInteger();
        public final AtomicInteger captures = new AtomicInteger();
        public final AtomicLong heldPaise = new AtomicLong();

        public Payments(Faults faults, boolean idempotent) {
            super("payment", faults, idempotent);
        }

        public void authorize(String sagaId, long paise) {
            perform("authorize", sagaId, () -> {
                authorizations.incrementAndGet();
                heldPaise.addAndGet(paise);
            });
        }

        public void voidAuthorization(String sagaId, long paise) {
            compensate("authorize", "void", sagaId, () -> {
                voids.incrementAndGet();
                heldPaise.addAndGet(-paise);
            });
        }

        public void capture(String sagaId) {
            perform("capture", sagaId, captures::incrementAndGet);
        }
    }

    /** kitchen-service: the restaurant accepts the order and starts cooking. */
    public static final class Kitchen extends Participant {
        public final AtomicInteger tickets = new AtomicInteger();

        public Kitchen(Faults faults) {
            super("kitchen", faults, true);
        }

        public void accept(String sagaId) {
            perform("accept", sagaId, tickets::incrementAndGet);
        }
    }

    /** delivery-service: find a rider. */
    public static final class Delivery extends Participant {
        public final AtomicInteger requests = new AtomicInteger();

        public Delivery(Faults faults) {
            super("delivery", faults, true);
        }

        public void requestRider(String sagaId) {
            perform("request-rider", sagaId, requests::incrementAndGet);
        }
    }
}
