# Chapter 5 code — Domain-Driven Design (FoodFleet Ordering context)

Docs: `docs/foundations/ch05-domain-driven-design.md`

| Package | DDD building blocks |
|---|---|
| `ordering/domain` | Value objects (`Money`, `Quantity`, `DeliveryAddress`, typed ids), entity (`OrderLine`), aggregate root (`Order`), domain events (`OrderEvent`), domain service (`PricingPolicy`), ports (`OrderRepository`, `MenuCatalog`, `DomainEventPublisher`) |
| `ordering/application` | Application service (`OrderApplicationService`), command (`PlaceOrderCommand`) |
| `ordering/infrastructure` | Anti-corruption layer (`CatalogAntiCorruptionLayer`, `CatalogTranslator`), repository adapter, composition root |
| `ordering/api` | REST adapter, Problem Details mapping |
| `catalog` | The upstream Catalog context's published API (simulated in-process) |

`domain` and `application` are plain Java with no Spring, enforced by `ArchitectureTest`.

```bash
cd code
mvn -B -pl chapters/ch05-domain-driven-design -am verify
mvn -pl chapters/ch05-domain-driven-design spring-boot:run      # port 8086

curl -s -X POST localhost:8086/api/v1/orders -H 'Content-Type: application/json' -d '{
  "customerId": "cust-42", "restaurantId": "rest-biryani-house",
  "address": {"line1": "12, 5th Cross", "area": "Koramangala", "city": "Bengaluru", "pincode": "560034"},
  "items": [{"menuItemId": "dum-biryani", "quantity": 2}, {"menuItemId": "boondi-raita", "quantity": 1}]
}' | jq .price      # totalPaise 87185 = ₹767 + ₹30 packaging + ₹39.85 GST + ₹35 delivery
```
