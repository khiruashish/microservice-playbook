| Journey | Priority | Required on the path | Optional (with a fallback) | Depth | If each is 99.9%: required only · if all were required | Each must reach, for the SLO |
|---|---|---|---|---|---|---|
| browse-menu | sheddable | `edge-gateway`, `customer-bff`, `menu-service`, `zone-service` | `dispatch-optimizer`, `eta-service`, `kitchen-service`, `pricing-service` | 3 | **99.60%** · 99.20% | 99.975% |
| place-order | critical | `edge-gateway`, `customer-bff`, `order-service`, `pricing-service` | `promotions-service` | 3 | **99.60%** · 99.50% | 99.975% |
| track-order | sheddable | `tracking-gateway` | `dispatch-optimizer`, `eta-service`, `kitchen-service` | 2 | **99.90%** · 99.60% | 99.500% |
