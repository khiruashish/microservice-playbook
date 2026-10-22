| Provider | Consumers with a contract | How |
|---|---|---|
| `customer-bff` | GraphQL: `edge-gateway` | Pact (ADR-063) |
| `partner-bff` | REST: `edge-gateway` | Pact (ADR-063) |
| `rider-bff` | REST: `edge-gateway` | Pact (ADR-063) |
| `tracking-gateway` | messages: `dispatch-optimizer` | AsyncAPI and the registry's FULL_TRANSITIVE check (ADR-027) |
| `order-service` | REST: `customer-bff`, `orders-mcp`<br>messages: `delivery-service`, `kitchen-service`, `notification-service`, `payment-service`, `promotions-service`, `reporting-service` | Pact (ADR-063); AsyncAPI and the registry's FULL_TRANSITIVE check (ADR-027) |
| `menu-service` | REST: `customer-bff`, `menus-mcp`, `partner-bff`<br>messages: `order-service`, `pricing-service`, `reporting-service` | Pact (ADR-063); AsyncAPI and the registry's FULL_TRANSITIVE check (ADR-027) |
| `kitchen-service` | REST/gRPC: `eta-service`, `partner-bff` | Pact (ADR-063); buf breaking on the .proto (ADR-023) |
| `delivery-service` | REST: `rider-bff`<br>messages: `dispatch-optimizer`, `eta-service`, `notification-service`, `reporting-service` | Pact (ADR-063); AsyncAPI and the registry's FULL_TRANSITIVE check (ADR-027) |
| `zone-service` | REST: `customer-bff`, `delivery-service`<br>messages: `delivery-service`, `menu-service` | Pact (ADR-063); AsyncAPI and the registry's FULL_TRANSITIVE check (ADR-027) |
| `dispatch-optimizer` | gRPC: `delivery-service`, `eta-service` | buf breaking on the .proto (ADR-023) |
| `promotions-service` | REST: `order-service` | Pact (ADR-063) |
| `payment-service` | messages: `order-service`, `reporting-service` | AsyncAPI and the registry's FULL_TRANSITIVE check (ADR-027) |
| `pricing-service` | REST: `menu-service`, `order-service` | Pact (ADR-063) |
| `eta-service` | gRPC: `customer-bff`, `tracking-gateway` | buf breaking on the .proto (ADR-023) |
| **Total** | **20 synchronous, 18 message** consumer–provider pairs | |
