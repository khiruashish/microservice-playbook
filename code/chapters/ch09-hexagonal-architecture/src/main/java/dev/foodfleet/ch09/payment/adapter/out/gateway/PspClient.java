package dev.foodfleet.ch09.payment.adapter.out.gateway;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * The payment provider's API exactly as the provider defines it (a fictional "PayKaro", shaped
 * like typical Indian payment gateways). Only the gateway adapter may see these types.
 */
@HttpExchange("/v1/payments")
public interface PspClient {

    @PostExchange
    PspPayment create(@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody CreatePayment body);

    @PostExchange("/{id}/capture")
    PspPayment capture(@PathVariable String id, @RequestBody AmountBody body);

    @PostExchange("/{id}/void")
    PspPayment voidPayment(@PathVariable String id);

    @PostExchange("/{id}/refunds")
    PspRefund refund(@PathVariable String id, @RequestBody AmountBody body);

    /** {@code method} is "upi" or "card"; amounts are in paise, as most Indian providers use. */
    record CreatePayment(long amount, String currency, String method, String methodReference, String merchantReference) {
    }

    record AmountBody(long amount) {
    }

    /** status: created | authorized | captured | failed | voided; errorCode only when failed. */
    record PspPayment(String id, String status, String errorCode) {
    }

    record PspRefund(String id, String status) {
    }
}
