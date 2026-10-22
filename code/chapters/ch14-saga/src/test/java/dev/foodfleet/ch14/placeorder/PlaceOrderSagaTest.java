package dev.foodfleet.ch14.placeorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch14.placeorder.Faults.Fault;
import dev.foodfleet.ch14.placeorder.PlaceOrderSaga.Request;
import dev.foodfleet.ch14.saga.SagaLog;
import dev.foodfleet.ch14.saga.SagaOrchestrator;
import dev.foodfleet.ch14.saga.SagaStatus;
import org.junit.jupiter.api.Test;

class PlaceOrderSagaTest {

    static final Request ORDER = new Request("o-1", 500_00, true);

    final ScriptedFaults faults = new ScriptedFaults();
    final Services.Orders orders = new Services.Orders(faults);
    final Services.Coupons coupons = new Services.Coupons(faults, 10);
    final Services.Payments payments = new Services.Payments(faults, true);
    final Services.Kitchen kitchen = new Services.Kitchen(faults);
    final Services.Delivery delivery = new Services.Delivery(faults);
    final SagaLog log = new SagaLog();

    SagaOrchestrator<Request> orchestrator(SagaOrchestrator.CrashPolicy crashes) {
        return new SagaOrchestrator<>(PlaceOrderSaga.definition(orders, coupons, payments, kitchen, delivery), log, 3, crashes);
    }

    SagaStatus run() {
        return orchestrator(SagaOrchestrator.CrashPolicy.NEVER).start("s-1", ORDER);
    }

    @Test
    void happyPath() {
        assertThat(run()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(orders.status).containsEntry("o-1", Services.Orders.Status.APPROVED);
        assertThat(payments.captures).hasValue(1);
        assertThat(coupons.remainingUses).hasValue(9);
        assertThat(delivery.requests).hasValue(1);
    }

    @Test
    void cardDeclinedUndoesEarlierStepsInReverseOrder() {
        faults.on("payment.authorize", Fault.REJECT);

        assertThat(run()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(log.history("s-1")).extracting(SagaLog.Entry::what).containsSubsequence(
                "done create order", "done reserve coupon", "failed authorize payment",
                "compensated reserve coupon", "compensated create order", "compensated");
        assertThat(coupons.remainingUses).hasValue(10);
        assertThat(orders.status).containsEntry("o-1", Services.Orders.Status.REJECTED);
        assertThat(payments.heldPaise).hasValue(0);
    }

    @Test
    void kitchenRejectsAtThePivot_authorizationIsVoided() {
        faults.on("kitchen.accept", Fault.REJECT);

        assertThat(run()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(payments.voids).hasValue(1);
        assertThat(payments.heldPaise).hasValue(0);
        assertThat(kitchen.tickets).hasValue(0);
    }

    @Test
    void lostReplyThenRetry_chargesOnce() {
        faults.on("payment.authorize", Fault.LOST_REPLY, Fault.REJECT);       // the retry must not be "rejected"

        assertThat(run()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(payments.authorizations).hasValue(1);
        assertThat(payments.heldPaise).hasValue(500_00);
    }

    @Test
    void afterThePivotThereIsNoCompensation_onlyRetries() {
        faults.on("delivery.request-rider", Fault.TRANSIENT, Fault.TRANSIENT, Fault.TRANSIENT);

        assertThat(run()).isEqualTo(SagaStatus.STUCK);
        assertThat(payments.voids).hasValue(0);
        assertThat(kitchen.tickets).hasValue(1);

        assertThat(orchestrator(SagaOrchestrator.CrashPolicy.NEVER).retryStuck("s-1", ORDER)).isEqualTo(SagaStatus.COMPLETED);
        assertThat(delivery.requests).hasValue(1);
    }

    @Test
    void anOrchestratorCrashIsResumedFromTheLog() {
        SagaOrchestrator.CrashPolicy crashAfterKitchen = (id, step) -> step.equals("kitchen accepts");
        assertThatThrownBy(() -> orchestrator(crashAfterKitchen).start("s-1", ORDER)).isInstanceOf(SagaOrchestrator.Crash.class);
        assertThat(log.unfinished()).containsExactly("s-1");

        assertThat(orchestrator(SagaOrchestrator.CrashPolicy.NEVER).resume("s-1", ORDER)).isEqualTo(SagaStatus.COMPLETED);
        assertThat(kitchen.tickets).as("the kitchen was called twice but cooks once").hasValue(1);
        assertThat(log.unfinished()).isEmpty();
    }
}
