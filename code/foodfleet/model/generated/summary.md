| Kind | How many | Which |
|---|---|---|
| **Edge** (the public entry points) | 2 | `edge-gateway`, `tracking-gateway` |
| **Backends for frontends** (one per client experience) | 3 | `customer-bff`, `partner-bff`, `rider-bff` |
| **Services** (the business capabilities) | 15 | `order-service`, `menu-service`, `kitchen-service`, `delivery-service`, `zone-service`, `dispatch-optimizer`, `promotions-service`, `customer-service`, `notification-service`, `payment-service`, `pricing-service`, `eta-service`, `reporting-service`, `orders-mcp`, `menus-mcp` |
| **Shared AI platform** (model calls) | 1 | `llm-gateway` |
| **Functions** (event-driven and bursty work) | 2 | `partner-webhooks`, `menu-photo-processor` |
| **Platform components** (no business data) | 10 | `keycloak`, `kafka`, `schema-registry`, `openbao`, `spire`, `istio-ambient`, `payment-ambassador`, `otel-collector`, `argo-cd`, `backstage` |
| **Synchronous calls** | 26 | [communication map](communication-map.md) |
| **Event topics and saga command channels** | 11 | [Kafka topic design](kafka-design.md) |
