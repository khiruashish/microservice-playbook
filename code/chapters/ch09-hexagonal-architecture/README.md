# Chapter 9 code — Hexagonal architecture: FoodFleet payment-service

Docs: `docs/architecture-patterns/ch09-hexagonal-clean-architecture.md`

```text
dev/foodfleet/ch09/payment/
├── domain/                     Payment aggregate, Money, PaymentMethod, states and rules (plain Java)
├── application/
│   ├── port/in/                driving ports: AuthorizePayment, CapturePayment, RefundPayment, GetPayment
│   ├── port/out/               driven ports: PaymentRepository, PaymentGateway
│   └── service/                PaymentService implements the driving ports (plain Java)
├── adapter/
│   ├── in/web/                 REST controller + Problem Details mapping
│   ├── in/events/              kitchen-rejection listener (a second driving adapter)
│   ├── out/persistence/        JDBC adapter (JdbcClient, optimistic locking)
│   └── out/gateway/            fake gateway, PSP adapter (HTTP service client + translator)
└── config/                     composition root: which adapter implements which port
```

| Test | What it proves | Spring? |
|---|---|---|
| `PaymentTest` | Aggregate rules | No |
| `PaymentServiceTest` | Use cases through driving ports, fake driven adapters | No |
| `PaymentRepositoryContract` → `InMemory…Test`, `Jdbc…Test` | Fake and real repository behave the same | No (embedded H2) |
| `PspTranslatorTest` | Provider vocabulary → our vocabulary | No |
| `PaymentControllerTest` | HTTP contract with the real core | `@WebMvcTest` |
| `PaymentServiceApplicationTest` | Assembled service, event adapter | `@SpringBootTest` |
| `ArchitectureTest` | Onion rule, framework-free core, adapters use ports | ArchUnit |

```bash
cd code
mvn -B -pl chapters/ch09-hexagonal-architecture -am verify
mvn -pl chapters/ch09-hexagonal-architecture spring-boot:run          # port 8089, H2, fake gateway

curl -si -X POST localhost:8089/api/payments -H 'Idempotency-Key: k-1' -H 'Content-Type: application/json' \
  -d '{"orderId":"o-1","amountPaise":87185,"method":{"type":"UPI","reference":"ravi@okaxis"}}'
```
