package dev.foodfleet.ch40.web;

import dev.foodfleet.ch40.tenancy.TenantId;
import dev.foodfleet.ch40.tenancy.TenantTransactions;
import dev.foodfleet.ch40.validation.FreeText;
import jakarta.validation.Valid;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The tenant and the customer come from the token; the request body only says what to order. Every query runs
 * through TenantTransactions, so even a query that forgets "AND tenant_id = ?" sees one tenant's rows. The
 * customer check stays in the query: row-level security separates tenants, not customers within one.
 */
@RestController
class OrderController {

    private final TenantTransactions tx;

    OrderController(TenantTransactions tx) {
        this.tx = tx;
    }

    @PostMapping("/api/orders")
    ResponseEntity<Map<String, Object>> place(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OrderRequest order)
            throws SQLException {
        String note = order.note() == null ? null : FreeText.clean("note", order.note(), 500, true);
        TenantId tenant = new TenantId(jwt.getClaimAsString("tenant"));
        long id = tx.inTenant(tenant, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO orders (tenant_id, id, customer_id, total_paise) "
                            + "VALUES (?, nextval('order_ids'), ?, 0) RETURNING id")) {
                ps.setString(1, tenant.value());
                ps.setString(2, jwt.getSubject());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        });
        return ResponseEntity.status(201).body(Map.of("id", id, "items", order.items().size(), "note", note == null ? "" : note));
    }

    @GetMapping("/api/orders/{id}")
    ResponseEntity<Map<String, Object>> get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) throws SQLException {
        TenantId tenant = new TenantId(jwt.getClaimAsString("tenant"));
        List<Map<String, Object>> found = tx.inTenant(tenant, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, total_paise FROM orders WHERE id = ? AND customer_id = ?")) {
                ps.setLong(1, id);
                ps.setString(2, jwt.getSubject());
                List<Map<String, Object>> rows = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(Map.of("id", rs.getLong(1), "totalPaise", rs.getLong(2)));
                    }
                }
                return rows;
            }
        });
        // 404 for "not yours" as well as "doesn't exist": the answer must not reveal that the id is taken
        return found.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(found.get(0));
    }
}
