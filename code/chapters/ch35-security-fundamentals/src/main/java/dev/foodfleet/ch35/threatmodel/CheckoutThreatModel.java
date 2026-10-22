package dev.foodfleet.ch35.threatmodel;

import static dev.foodfleet.ch35.threatmodel.ElementType.AUDIT_LOG;
import static dev.foodfleet.ch35.threatmodel.ElementType.DATA_STORE;
import static dev.foodfleet.ch35.threatmodel.ElementType.EXTERNAL_ENTITY;
import static dev.foodfleet.ch35.threatmodel.ElementType.PROCESS;
import static dev.foodfleet.ch35.threatmodel.Stride.DENIAL_OF_SERVICE;
import static dev.foodfleet.ch35.threatmodel.Stride.ELEVATION_OF_PRIVILEGE;
import static dev.foodfleet.ch35.threatmodel.Stride.INFORMATION_DISCLOSURE;
import static dev.foodfleet.ch35.threatmodel.Stride.REPUDIATION;
import static dev.foodfleet.ch35.threatmodel.Stride.SPOOFING;
import static dev.foodfleet.ch35.threatmodel.Stride.TAMPERING;

/**
 * FoodFleet's checkout: a customer places an order, the restaurant is notified, the payment is captured. Five
 * trust zones; the flows between them are where most threats live. References point to the chapter or class
 * that implements or tests each control.
 */
public final class CheckoutThreatModel {

    private CheckoutThreatModel() {
    }

    public static ThreatModel build() {
        ThreatModel m = new ThreatModel("FoodFleet checkout");
        // trust zones: internet, edge, services, data, partners
        m.element("customer-app", EXTERNAL_ENTITY, "internet", "Customer mobile and web apps")
         .element("restaurant-tablet", EXTERNAL_ENTITY, "internet", "Restaurant order tablets")
         .element("payment-provider", EXTERNAL_ENTITY, "partners", "Card payment provider")
         .element("restaurant-webhook", EXTERNAL_ENTITY, "partners", "Restaurants' own systems, notified by webhook")
         .element("api-gateway", PROCESS, "edge", "Public entry point: TLS, authentication, rate limits")
         .element("order-service", PROCESS, "services", "Orders: placing, changing, cancelling, refunds")
         .element("payment-service", PROCESS, "services", "Captures and refunds through the provider")
         .element("orders-db", DATA_STORE, "data", "order-service's PostgreSQL")
         .element("payments-db", DATA_STORE, "data", "payment-service's PostgreSQL (provider tokens, no card data)")
         .element("order-events", DATA_STORE, "data", "Kafka topic with order events")
         .element("audit-log", AUDIT_LOG, "data", "Who did what to which order or payment");
        m.flow("f1-app-gateway", "customer-app", "api-gateway", "Orders, addresses, phone numbers, tokens")
         .flow("f2-tablet-gateway", "restaurant-tablet", "api-gateway", "Accept or reject orders")
         .flow("f3-gateway-orders", "api-gateway", "order-service", "Authenticated API calls")
         .flow("f4-orders-db", "order-service", "orders-db", "SQL")
         .flow("f5-orders-events", "order-service", "order-events", "Order events via the outbox")
         .flow("f6-events-payments", "order-events", "payment-service", "Payment requests")
         .flow("f7-payments-db", "payment-service", "payments-db", "SQL")
         .flow("f8-payments-provider", "payment-service", "payment-provider", "Capture and refund calls")
         .flow("f9-orders-webhook", "order-service", "restaurant-webhook", "New-order notifications")
         .flow("f10-services-audit", "order-service", "audit-log", "Audit records");

        // Spoofing
        m.mitigate(SPOOFING, "OIDC login; short-lived access tokens; login throttling and PBKDF2 for passwords",
                "ch36, LoginThrottle, PasswordHasher", "customer-app")
         .mitigate(SPOOFING, "Per-device OIDC login; tokens carry the owner's restaurant ids", "ch36, Caller",
                "restaurant-tablet")
         .mitigate(SPOOFING, "Provider callbacks verified by HMAC signature; provider called over TLS by host name",
                "ch40", "payment-provider")
         .mitigate(SPOOFING, "Webhook URLs registered only by authenticated owners; HTTPS only; internal targets "
                + "refused", "OutboundUrlPolicy", "restaurant-webhook")
         .mitigate(SPOOFING, "Public TLS certificates for the API host names", "ch40", "api-gateway")
         .mitigate(SPOOFING, "mTLS workload identities between services", "ch38", "order-service", "payment-service");
        // Tampering
        m.mitigate(TAMPERING, "Signed images, read-only root filesystems, no shell in containers", "ch41",
                "api-gateway", "order-service", "payment-service")
         .mitigate(TAMPERING, "Only the owning service's credentials can write; credentials from Vault",
                "ch12, ch39", "orders-db", "payments-db")
         .mitigate(TAMPERING, "Kafka ACLs: only order-service produces; events validated against schemas",
                "ch27, ch38", "order-events")
         .mitigate(TAMPERING, "Append-only role; entries hash-chained and shipped to write-once storage", "Part 6",
                "audit-log")
         .mitigate(TAMPERING, "TLS 1.3 from the internet and to partners; webhook bodies HMAC-signed", "ch40",
                "f1-app-gateway", "f2-tablet-gateway", "f8-payments-provider", "f9-orders-webhook")
         .mitigate(TAMPERING, "mTLS inside the cluster, TLS to databases and Kafka", "ch38",
                "f3-gateway-orders", "f4-orders-db", "f5-orders-events", "f6-events-payments", "f7-payments-db",
                "f10-services-audit")
         .mitigate(TAMPERING, "Only authorised fields can change (OrderPatch); amounts are never taken from clients",
                "OrderPatch, OrderAccessPolicy", "order-service")
         .mitigate(TAMPERING, "Refund amounts come from the stored capture, not the request", "ch30",
                "payment-service");
        // Repudiation
        m.mitigate(REPUDIATION, "Every change audited with the authenticated caller and request id", "ch28, audit-log",
                "customer-app", "restaurant-tablet", "order-service")
         .mitigate(REPUDIATION, "Provider references stored with every capture and refund; daily reconciliation",
                "ch34", "payment-provider", "payment-service")
         .mitigate(REPUDIATION, "Access logs with correlation ids, kept 90 days", "ch28", "api-gateway")
         .mitigate(REPUDIATION, "Audit entries can't be altered or deleted by services; integrity checked daily",
                "Part 6", "audit-log")
         .accept(REPUDIATION, "We can only prove the restaurant's endpoint answered 2xx; tablets also poll for "
                + "new orders", "restaurant operations", "2026-03-31", "restaurant-webhook");
        // Information disclosure
        m.mitigate(INFORMATION_DISCLOSURE, "No tokens or personal data in logs; errors without internals", "ch40",
                "api-gateway")
         .mitigate(INFORMATION_DISCLOSURE, "Per-audience views; others' orders answer 404; no internal fields "
                + "serialised", "OrderView, OrderService", "order-service")
         .mitigate(INFORMATION_DISCLOSURE, "No card data anywhere: provider tokens only (PCI DSS scope)", "ch41",
                "payment-service", "payments-db")
         .mitigate(INFORMATION_DISCLOSURE, "Encryption at rest; personal data minimised; access by role", "ch39",
                "orders-db")
         .mitigate(INFORMATION_DISCLOSURE, "Events carry ids, not addresses or phone numbers; topic ACLs", "ch27",
                "order-events")
         .mitigate(INFORMATION_DISCLOSURE, "Readable only by the security team", "Part 6", "audit-log")
         .mitigate(INFORMATION_DISCLOSURE, "TLS 1.3 / mTLS on every flow", "ch38, ch40",
                "f1-app-gateway", "f2-tablet-gateway", "f3-gateway-orders", "f4-orders-db", "f5-orders-events",
                "f6-events-payments", "f7-payments-db", "f8-payments-provider", "f9-orders-webhook",
                "f10-services-audit");
        // Denial of service
        m.mitigate(DENIAL_OF_SERVICE, "Rate limits per client and address; edge DDoS protection", "ch40",
                "api-gateway", "f1-app-gateway")
         .mitigate(DENIAL_OF_SERVICE, "Bounded pages and request sizes; timeouts and bulkheads", "PageLimits, ch18",
                "order-service")
         .mitigate(DENIAL_OF_SERVICE, "Timeouts and a circuit breaker on the provider; payments retried from the "
                + "outbox", "ch18, ch30", "payment-service", "f8-payments-provider")
         .mitigate(DENIAL_OF_SERVICE, "Pool budgets and statement timeouts", "ch33", "orders-db", "payments-db",
                "f4-orders-db", "f7-payments-db")
         .mitigate(DENIAL_OF_SERVICE, "Producer quotas; consumers lag-alerted", "ch25", "order-events",
                "f5-orders-events", "f6-events-payments")
         .mitigate(DENIAL_OF_SERVICE, "Written asynchronously with back-pressure; separate storage", "Part 6",
                "audit-log", "f10-services-audit")
         .mitigate(DENIAL_OF_SERVICE, "Webhooks delivered asynchronously with timeouts; a slow endpoint can't hold "
                + "orders", "WebhookClient", "f9-orders-webhook")
         .mitigate(DENIAL_OF_SERVICE, "Gateway timeouts and retry budgets", "ch18", "f3-gateway-orders")
         .accept(DENIAL_OF_SERVICE, "Tablets on poor connections may miss updates; restaurants can call support",
                "restaurant operations", "2026-03-31", "f2-tablet-gateway");
        // Elevation of privilege
        m.mitigate(ELEVATION_OF_PRIVILEGE, "Authentication only; admin routes not exposed publicly", "ch10, ch40",
                "api-gateway")
         .mitigate(ELEVATION_OF_PRIVILEGE, "Every operation checked by OrderAccessPolicy with the authenticated caller",
                "OrderAccessPolicy", "order-service")
         .mitigate(ELEVATION_OF_PRIVILEGE, "Refunds re-check the SUPPORT role in payment-service; idempotency keys",
                "ch28", "payment-service");
        return m;
    }
}
