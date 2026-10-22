# Chapter 14 code — Saga pattern: FoodFleet place-order saga

Docs: `docs/architecture-patterns/ch14-saga-pattern.md`

| Package | What it shows |
|---|---|
| `saga/` | A small orchestration engine: compensatable / pivot / retriable steps, a saga log, resume after a crash, compensation in reverse order, STUCK sagas retried later |
| `placeorder/` | The saga (create order → reserve coupon → authorize payment → **kitchen accepts (pivot)** → capture → rider → approve) and idempotent participants with fault injection |
| `choreography/` | The same flow as events between services, with redelivery and a dead-letter trace |
| `simulation/` | 10,000 sagas with injected rejections, timeouts, lost replies and orchestrator crashes |

```bash
cd code
mvn -B -pl chapters/ch14-saga -am verify
java -cp chapters/ch14-saga/target/classes dev.foodfleet.ch14.simulation.SagaSimulation
```

Output (verified):

```text
== idempotent participants ==
10000 sagas, 351 orchestrator crashes (resumed from the log)
first pass:  {COMPLETED=8638, COMPENSATED=1321, STUCK=41}
after retry: {COMPLETED=8679, COMPENSATED=1321}
authorizations voided: 475
money held: ₹4,339,500, expected ₹4,339,500, extra ₹0 (0 double charges)
coupons leaked: 0, approved orders without a kitchen ticket: 0

== payment-service NOT idempotent ==
10000 sagas, 348 orchestrator crashes (resumed from the log)
first pass:  {COMPLETED=8540, COMPENSATED=1417, STUCK=43}
after retry: {COMPLETED=8583, COMPENSATED=1417}
authorizations voided: 518
money held: ₹4,374,000, expected ₹4,291,500, extra ₹82,500 (165 double charges)
coupons leaked: 0, approved orders without a kitchen ticket: 0
```
