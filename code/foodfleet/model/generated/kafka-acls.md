| Principal | Write | Read |
|---|---|---|
| `User:tracking-gateway` | `tracking-positions` | `tracking-positions` |
| `User:order-service` | `order-events`, `saga commands: payment.authorize / capture / void`, `saga commands: kitchen.accept`, `saga commands: delivery.request-rider`, `saga commands: coupon reserve / release` | `foodfleet.catalog.menu-items`, `payment-events` |
| `User:menu-service` | `foodfleet.catalog.menu-items` | `foodfleet.zones.zone` |
| `User:kitchen-service` | – | `order-events`, `saga commands: kitchen.accept` |
| `User:delivery-service` | `foodfleet.delivery.rider-status`, `wallet-events` | `order-events`, `foodfleet.zones.zone`, `saga commands: delivery.request-rider` |
| `User:zone-service` | `foodfleet.zones.zone` | – |
| `User:dispatch-optimizer` | – | `tracking-positions`, `foodfleet.delivery.rider-status` |
| `User:promotions-service` | – | `saga commands: coupon reserve / release` |
| `User:notification-service` | – | `order-events`, `wallet-events` |
| `User:payment-service` | `payment-events` | `saga commands: payment.authorize / capture / void` |
| `User:pricing-service` | – | `foodfleet.catalog.menu-items` |
| `User:eta-service` | – | `foodfleet.delivery.rider-status` |
| `User:reporting-service` | – | `order-events`, `foodfleet.catalog.menu-items`, `payment-events`, `wallet-events` |
