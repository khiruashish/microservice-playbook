| Service | Team | Store | What it holds |
|---|---|---|---|
| `edge-gateway` | Platform | Redis | rate-limit counters (copies, ephemeral) |
| `tracking-gateway` | Logistics | Redis | per-order pub/sub channels and live rider positions (ephemeral) |
| `order-service` | Checkout | PostgreSQL | orders, order lines, carts, saga log, outbox, inbox |
| `menu-service` | Restaurant Partner | PostgreSQL | restaurants, menus, menu cards, outbox |
| `kitchen-service` | Restaurant Partner | PostgreSQL | tickets, inbox, outbox |
| `delivery-service` | Logistics | PostgreSQL | assignments, wallet event store and projections, outbox |
| `zone-service` | Logistics | PostgreSQL | zones, outbox |
| `dispatch-optimizer` | Dispatch | PostgreSQL | rider history partitioned by day (BRIN) |
| `dispatch-optimizer` | Dispatch | Redis | live rider positions (ephemeral) |
| `promotions-service` | Growth | PostgreSQL | coupons, redemptions, inbox, outbox |
| `customer-service` | Growth | PostgreSQL | profiles, addresses |
| `notification-service` | Growth | PostgreSQL | notification log, inbox |
| `payment-service` | Payments | PostgreSQL | payments, idempotency keys, inbox, outbox |
| `pricing-service` | Checkout | PostgreSQL | price rules |
| `pricing-service` | Checkout | Redis | price cache (version-guarded copies) |
| `reporting-service` | Platform | PostgreSQL | reporting store (facts by key and version, materialized views) |
