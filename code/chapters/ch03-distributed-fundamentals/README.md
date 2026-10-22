# Chapter 3 code — Distributed Systems Fundamentals

Docs: `docs/foundations/ch03-distributed-systems-fundamentals.md`

| Package | Concept | Test |
|---|---|---|
| `net` | Fallacy #1: drops, duplicates, delays, reordering (deterministic, seeded) | `UnreliableNetworkTest` |
| `clock` | Lamport, vector and hybrid logical clocks | `ClocksTest` |
| `replication` | Quorums (R + W > N), stale reads, CP vs AP under partition, LWW convergence | `QuorumRegisterTest` |
| `idempotency` | At-most-once vs at-least-once vs exactly-once effects; `Idempotency-Key` executor | `ExactlyOnceEffectsTest`, `IdempotencyKeyExecutorTest` |
| `refund` | Spring Boot 4 endpoint: idempotent refunds with `Idempotency-Key` (port 8084) | `RefundControllerTest` |

Only `refund` uses Spring. Everything else is plain Java 21+.

```bash
cd code
mvn -B -pl chapters/ch03-distributed-fundamentals -am verify
mvn -pl chapters/ch03-distributed-fundamentals spring-boot:run

KEY=$(uuidgen)
for i in 1 2; do
  curl -si -X POST localhost:8084/api/v1/refunds -H "Idempotency-Key: $KEY" \
       -H 'Content-Type: application/json' -d '{"orderId":"order-101","amountPaise":34900}' \
       | grep -E "HTTP|Idempotent-Replayed|refundId"
done   # same refundId twice; second response says Idempotent-Replayed: true
```
