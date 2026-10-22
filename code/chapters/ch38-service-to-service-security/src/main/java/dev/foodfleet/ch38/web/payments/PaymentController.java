package dev.foodfleet.ch38.web.payments;

import dev.foodfleet.ch38.spiffe.SpiffeId;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/** The routes the peer policy and the token scopes protect; the caller's workload identity is available for audit. */
@RestController
public class PaymentController {

    @PostMapping("/payments/{orderId}/capture")
    public ResponseEntity<Map<String, Object>> capture(@PathVariable long orderId,
            @RequestAttribute(PeerPolicyFilter.PEER_ATTRIBUTE) SpiffeId caller) {
        return ResponseEntity.ok(Map.of("orderId", orderId, "status", "CAPTURED", "requestedBy", caller.toString()));
    }

    @PostMapping("/payments/{orderId}/refund")
    public ResponseEntity<Map<String, Object>> refund(@PathVariable long orderId) {
        return ResponseEntity.ok(Map.of("orderId", orderId, "status", "REFUNDED"));
    }

    @PostMapping("/internal/settlements/run")
    public ResponseEntity<Map<String, Object>> settle() {
        return ResponseEntity.accepted().body(Map.of("status", "STARTED"));
    }
}
