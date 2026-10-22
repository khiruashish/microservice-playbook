package dev.foodfleet.ch28.payments;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /payments} with the {@code Idempotency-Key} header (IETF httpapi draft: a Structured
 * Field string, e.g. {@code Idempotency-Key: "8e03978e-40d5-43e8-bc93-6894a57f9324"}). The client
 * id comes from the gateway; keys are scoped per client.
 */
@RestController
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/payments")
    public ResponseEntity<String> pay(@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                      @RequestHeader("X-Client-Id") String clientId,
                                      @RequestBody PaymentRequest request) {
        String key = parseKey(idempotencyKey);
        if (key == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body("{\"title\":\"Idempotency-Key header required for POST /payments\"}");
        }
        PaymentService.Outcome outcome = payments.pay(clientId, key, request);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(outcome.status())
                .contentType(outcome.status() < 400 ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_PROBLEM_JSON);
        if (outcome.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        if (outcome.status() == 409 || outcome.status() == 503) {
            response.header("Retry-After", "2");
        }
        return response.body(outcome.body());
    }

    /** An sf-string ("...") or, leniently, a bare token; 1-255 printable characters. */
    static String parseKey(String header) {
        if (header == null) {
            return null;
        }
        String v = header.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        return v.isEmpty() || v.length() > 255 || !v.chars().allMatch(c -> c >= 0x21 && c <= 0x7e) ? null : v;
    }
}
