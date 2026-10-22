package dev.foodfleet.lab18;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Queries still say which tenant they mean. Row-level security is the second line: if a query forgets the predicate,
 * the database returns the current tenant's rows only (or none), never another's (chapter 75: 10,000 rows of 20
 * tenants without RLS, 500 of one with it).
 */
@Repository
public class Orders {

    public record Order(String tenantId, long id, String customerId, long totalPaise, Instant createdAt) {
    }

    private final JdbcClient jdbc;

    public Orders(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Order> recent(String tenantId, int limit) {
        return jdbc.sql("""
                        SELECT tenant_id, id, customer_id, total_paise, created_at FROM orders
                        WHERE tenant_id = :tenant ORDER BY created_at DESC LIMIT :limit""")
                .param("tenant", tenantId).param("limit", limit)
                .query(Order.class).list();
    }

    public long place(String tenantId, String customerId, long totalPaise) {
        return jdbc.sql("""
                        INSERT INTO orders (tenant_id, id, customer_id, total_paise)
                        VALUES (:tenant, nextval('order_ids'), :customer, :total) RETURNING id""")
                .param("tenant", tenantId).param("customer", customerId).param("total", totalPaise)
                .query(Long.class).single();
    }

    /** No tenant predicate on purpose: what a forgotten WHERE clause returns under RLS. */
    long countAllVisible() {
        return jdbc.sql("SELECT count(*) FROM orders").query(Long.class).single();
    }
}
