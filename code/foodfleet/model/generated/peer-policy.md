| Service | Namespace | May be called by (SPIFFE principal) |
|---|---|---|
| `edge-gateway` | `edge-gateway` | `cluster.local/ns/istio-ingress/sa/ingress-gateway` |
| `customer-bff` | `customer-bff` | `cluster.local/ns/edge-gateway/sa/edge-gateway` |
| `partner-bff` | `partner-bff` | `cluster.local/ns/edge-gateway/sa/edge-gateway` |
| `rider-bff` | `rider-bff` | `cluster.local/ns/edge-gateway/sa/edge-gateway` |
| `tracking-gateway` | `tracking-gateway` | `cluster.local/ns/istio-ingress/sa/ingress-gateway` |
| `order-service` | `orders` | `cluster.local/ns/customer-bff/sa/customer-bff`<br>`cluster.local/ns/orders-mcp/sa/orders-mcp` |
| `menu-service` | `menu-service` | `cluster.local/ns/customer-bff/sa/customer-bff`<br>`cluster.local/ns/menus-mcp/sa/menus-mcp`<br>`cluster.local/ns/partner-bff/sa/partner-bff` |
| `kitchen-service` | `kitchen-service` | `cluster.local/ns/eta-service/sa/eta-service`<br>`cluster.local/ns/partner-bff/sa/partner-bff` |
| `delivery-service` | `delivery-service` | `cluster.local/ns/rider-bff/sa/rider-bff` |
| `zone-service` | `zone-service` | `cluster.local/ns/customer-bff/sa/customer-bff`<br>`cluster.local/ns/delivery-service/sa/delivery-service` |
| `dispatch-optimizer` | `dispatch-optimizer` | `cluster.local/ns/delivery-service/sa/delivery-service`<br>`cluster.local/ns/eta-service/sa/eta-service` |
| `promotions-service` | `promotions-service` | `cluster.local/ns/orders/sa/order-service` |
| `customer-service` | `customer-service` | nobody, in the model |
| `notification-service` | `notification-service` | nobody, in the model |
| `payment-service` | `payments` | nobody, in the model |
| `pricing-service` | `pricing-service` | `cluster.local/ns/menu-service/sa/menu-service`<br>`cluster.local/ns/orders/sa/order-service` |
| `eta-service` | `eta-service` | `cluster.local/ns/customer-bff/sa/customer-bff`<br>`cluster.local/ns/tracking-gateway/sa/tracking-gateway` |
| `reporting-service` | `reporting-service` | nobody, in the model |
| `llm-gateway` | `llm-gateway` | nobody, in the model |
| `orders-mcp` | `orders-mcp` | nobody, in the model |
| `menus-mcp` | `menus-mcp` | nobody, in the model |
