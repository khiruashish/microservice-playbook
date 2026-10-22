package dev.foodfleet.lab18;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The tenant comes from the token (a claim the identity provider sets per partner organisation), never from a header,
 * a path or a query parameter the caller could change.
 */
@RestController
class OrdersController {

    private final TenantTransactions tenants;
    private final Orders orders;
    private final TenantBulkheads bulkheads;

    OrdersController(TenantTransactions tenants, Orders orders, TenantBulkheads bulkheads) {
        this.tenants = tenants;
        this.orders = orders;
        this.bulkheads = bulkheads;
    }

    record NewOrder(String customerId, long totalPaise) {
    }

    @GetMapping("/orders")
    List<Orders.Order> recent(@AuthenticationPrincipal Jwt token) {
        String tenant = tenant(token);
        return tenants.inTenant(tenant, () -> orders.recent(tenant, 20));
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> place(@AuthenticationPrincipal Jwt token, @RequestBody NewOrder order) {
        String tenant = tenant(token);
        return Map.of("id", tenants.inTenant(tenant, () -> orders.place(tenant, order.customerId(), order.totalPaise())));
    }

    @GetMapping("/reports/monthly")
    Map<String, Object> monthly(@AuthenticationPrincipal Jwt token) {
        String tenant = tenant(token);
        return bulkheads.report(tenant, () -> tenants.inTenant(tenant, () -> Map.of("tenant", tenant,
                "orders", orders.recent(tenant, 10_000).size())));
    }

    @ExceptionHandler(TenantBulkheads.Busy.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    Map<String, String> busy(TenantBulkheads.Busy e) {
        return Map.of("error", e.getMessage());
    }

    static String tenant(Jwt token) {
        String tenant = token.getClaimAsString("tenant_id");
        if (tenant == null) {
            throw new org.springframework.security.access.AccessDeniedException("token has no tenant");
        }
        return tenant;
    }
}
