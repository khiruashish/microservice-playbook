| Caller | Dependency | Protocol | On a journey | The question to answer |
|---|---|---|---|---|
| `edge-gateway` | `customer-bff` | GraphQL | – | none possible: the BFF is the experience; make it as available as the edge |
| `edge-gateway` | `partner-bff` | REST | – | none possible: the BFF is the experience; make it as available as the edge |
| `edge-gateway` | `rider-bff` | REST | – | none possible: the BFF is the experience; make it as available as the edge |
| `customer-bff` | `menu-service` | REST | browse-menu | which screens still work without `menu-service`? |
| `customer-bff` | `order-service` | REST | place-order | which screens still work without `order-service`? |
| `eta-service` | `kitchen-service` | gRPC | – | can `eta-service` answer without `kitchen-service`, from a copy or a default? |
| `eta-service` | `dispatch-optimizer` | gRPC | – | can `eta-service` answer without `dispatch-optimizer`, from a copy or a default? |
| `delivery-service` | `dispatch-optimizer` | gRPC | – | can `delivery-service` answer without `dispatch-optimizer`, from a copy or a default? |
| `delivery-service` | `zone-service` | REST | – | can `delivery-service` answer without `zone-service`, from a copy or a default? |
| `payment-service` | `payment-provider` | REST | – | decided in the ambassador's policy (ADR-017); record it in the model |
| `partner-bff` | `menu-service` | REST | – | which screens still work without `menu-service`? |
| `partner-bff` | `kitchen-service` | REST | – | which screens still work without `kitchen-service`? |
| `rider-bff` | `delivery-service` | REST | – | which screens still work without `delivery-service`? |
| `orders-mcp` | `order-service` | REST | – | return a tool error the assistant can explain |
| `menus-mcp` | `menu-service` | REST | – | return a tool error the assistant can explain |
