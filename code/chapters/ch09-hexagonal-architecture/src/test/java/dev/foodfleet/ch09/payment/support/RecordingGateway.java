package dev.foodfleet.ch09.payment.support;

import dev.foodfleet.ch09.payment.application.port.out.PaymentGateway;
import dev.foodfleet.ch09.payment.domain.Money;
import dev.foodfleet.ch09.payment.domain.PaymentId;
import dev.foodfleet.ch09.payment.domain.PaymentMethod;
import java.util.ArrayList;
import java.util.List;

/** A scriptable gateway that records every call, so tests can assert the side effects. */
public class RecordingGateway implements PaymentGateway {

    public final List<String> calls = new ArrayList<>();
    private Authorization nextAuthorization;
    private boolean down;

    public RecordingGateway approveAll() {
        nextAuthorization = null;
        return this;
    }

    public RecordingGateway declineNext(String reason) {
        nextAuthorization = new Declined(reason);
        return this;
    }

    public RecordingGateway down(boolean down) {
        this.down = down;
        return this;
    }

    @Override
    public Authorization authorize(PaymentId paymentId, Money amount, PaymentMethod method) {
        record("authorize " + paymentId + " " + amount);
        Authorization result = nextAuthorization != null ? nextAuthorization : new Approved("gw_" + paymentId);
        nextAuthorization = null;
        return result;
    }

    @Override
    public void capture(String gatewayReference, Money amount) {
        record("capture " + gatewayReference + " " + amount);
    }

    @Override
    public void voidAuthorization(String gatewayReference) {
        record("void " + gatewayReference);
    }

    @Override
    public void refund(String gatewayReference, Money amount) {
        record("refund " + gatewayReference + " " + amount);
    }

    private void record(String call) {
        if (down) {
            throw new GatewayUnavailableException("simulated outage: " + call, null);
        }
        calls.add(call);
    }
}
