package dev.foodfleet.ch09.payment.adapter.out.gateway;

import dev.foodfleet.ch09.payment.application.port.out.PaymentGateway;
import dev.foodfleet.ch09.payment.domain.Money;
import dev.foodfleet.ch09.payment.domain.PaymentId;
import dev.foodfleet.ch09.payment.domain.PaymentMethod;
import java.util.function.Supplier;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/** Driven adapter for the real provider: HTTP service client + translator + error mapping. */
public class PspPaymentGateway implements PaymentGateway {

    private final PspClient client;

    public PspPaymentGateway(PspClient client) {
        this.client = client;
    }

    @Override
    public Authorization authorize(PaymentId paymentId, Money amount, PaymentMethod method) {
        return call("authorize " + paymentId, () -> PspTranslator.toAuthorization(client.create(
                paymentId.value(),                      // our id is the provider's idempotency key
                new PspClient.CreatePayment(amount.paise(), "INR", PspTranslator.toPspMethod(method),
                        method.reference(), paymentId.value()))));
    }

    @Override
    public void capture(String gatewayReference, Money amount) {
        call("capture " + gatewayReference, () -> client.capture(gatewayReference, new PspClient.AmountBody(amount.paise())));
    }

    @Override
    public void voidAuthorization(String gatewayReference) {
        call("void " + gatewayReference, () -> client.voidPayment(gatewayReference));
    }

    @Override
    public void refund(String gatewayReference, Money amount) {
        call("refund " + gatewayReference, () -> client.refund(gatewayReference, new PspClient.AmountBody(amount.paise())));
    }

    /** Technical failures become the port's exception; the core never sees Spring's HTTP exceptions. */
    private static <T> T call(String what, Supplier<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new GatewayUnavailableException("Payment provider unavailable: " + what, e);
        }
    }
}
