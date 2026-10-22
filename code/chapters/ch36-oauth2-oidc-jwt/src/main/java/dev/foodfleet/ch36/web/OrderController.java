package dev.foodfleet.ch36.web;

import dev.foodfleet.ch36.exchange.TokenExchangeClient;
import dev.foodfleet.ch36.jwt.VerifiedJwt;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scopes say what the client application may do on the user's behalf; whether this user may touch this order is
 * still the service's decision (chapter 35's OrderAccessPolicy). Calling payment-service uses an exchanged token,
 * not the user's.
 */
@RestController
public class OrderController {

    private final TokenExchangeClient exchange;

    public OrderController(TokenExchangeClient exchange) {
        this.exchange = exchange;
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Map<String, Object>> get(@RequestAttribute(BearerTokenFilter.TOKEN) VerifiedJwt token,
                                                   @PathVariable long id) {
        if (!token.hasScope("orders:read")) {
            return insufficientScope("orders:read");
        }
        return ResponseEntity.ok(Map.of("id", id, "customer", token.subject()));
    }

    @PostMapping("/orders/{id}/payment")
    public ResponseEntity<Map<String, Object>> pay(@RequestAttribute(BearerTokenFilter.TOKEN) VerifiedJwt token,
                                                   @RequestAttribute(BearerTokenFilter.RAW_TOKEN) String raw,
                                                   @PathVariable long id) {
        if (!token.hasScope("orders:write")) {
            return insufficientScope("orders:write");
        }
        String forPayments = exchange.tokenFor(raw, "payment-service", "payments:capture");
        // the call to payment-service with "Authorization: Bearer " + forPayments is chapter 22's RestClient
        return ResponseEntity.accepted().body(Map.of("order", id, "paymentTokenAudience", "payment-service",
                "tokenLength", forPayments.length()));
    }

    private static ResponseEntity<Map<String, Object>> insufficientScope(String scope) {
        return ResponseEntity.status(403)
                .header("WWW-Authenticate", "Bearer error=\"insufficient_scope\", scope=\"" + scope + "\"")
                .body(Map.of("error", "insufficient_scope"));
    }
}
