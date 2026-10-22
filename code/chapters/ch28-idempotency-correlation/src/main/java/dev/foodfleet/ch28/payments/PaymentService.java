package dev.foodfleet.ch28.payments;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Exactly-once payment creation over an at-least-once network:
 * <ol>
 *   <li>claim the key (one statement, committed) before calling anyone;</li>
 *   <li>call the provider outside any transaction, passing a key derived from ours;</li>
 *   <li>record the payment and the response together, in one transaction.</li>
 * </ol>
 * Duplicates get the stored response (replayed), 409 while the first is in progress, or 422 if the
 * key is reused for a different request. If the provider's answer is unknown, the key stays
 * IN_PROGRESS until its lease expires; the client's retry then re-claims it and calls the provider
 * again with the same provider key, which returns the original charge instead of a second one.
 */
@Service
public class PaymentService {

    public record Outcome(int status, String body, boolean replayed) {
    }

    static final Duration LEASE = Duration.ofSeconds(30);

    private final IdempotencyKeys keys;
    private final PaymentProvider provider;
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    public PaymentService(IdempotencyKeys keys, PaymentProvider provider, JdbcClient jdbc, TransactionTemplate transaction) {
        this.keys = keys;
        this.provider = provider;
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    public Outcome pay(String clientId, String key, PaymentRequest request) {
        String hash = fingerprint(request);
        if (!keys.claim(clientId, key, hash, LEASE)) {
            return keys.find(clientId, key).map(existing -> {
                if (!existing.requestHash().equals(hash)) {
                    return new Outcome(422, problem("Idempotency-Key reused with a different request"), false);
                }
                if (existing.status() == IdempotencyKeys.Status.COMPLETED) {
                    return new Outcome(existing.responseStatus(), existing.responseBody(), true);
                }
                return new Outcome(409, problem("A request with this Idempotency-Key is in progress"), false);
            }).orElseGet(() -> new Outcome(409, problem("Retry"), false));     // released in between: retry
        }
        String reference;
        try {
            reference = provider.charge(clientId + ":" + key, request);
        } catch (PaymentProvider.Unavailable e) {
            keys.release(clientId, key);                                       // nothing happened: free the key
            return new Outcome(503, problem("Payment provider unavailable, retry"), false);
        } catch (PaymentProvider.OutcomeUnknown e) {
            // Maybe charged: keep the key IN_PROGRESS; after the lease a retry re-asks the provider.
            return new Outcome(503, problem("Payment outcome unknown, retry later"), false);
        }
        String paymentId = "p-" + UUID.randomUUID();
        String body = "{\"paymentId\":\"%s\",\"orderId\":\"%s\",\"amountPaise\":%d,\"providerReference\":\"%s\"}"
                .formatted(paymentId, request.orderId(), request.amountPaise(), reference);
        transaction.executeWithoutResult(tx -> {
            jdbc.sql("INSERT INTO payments (id, order_id, amount_paise, provider_reference) VALUES (:id, :order, :amount, :ref)")
                    .param("id", paymentId).param("order", request.orderId()).param("amount", request.amountPaise())
                    .param("ref", reference).update();
            keys.complete(clientId, key, 201, body);
        });
        return new Outcome(201, body, false);
    }

    static String fingerprint(PaymentRequest request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(request.canonical().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String problem(String detail) {
        return "{\"title\":\"%s\"}".formatted(detail);
    }
}
