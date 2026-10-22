# Chapter 10 code — API gateway and Backend for Frontend

Docs: `docs/architecture-patterns/ch10-api-gateway-bff.md`

| Module | Port | What it shows |
|---|---|---|
| `gateway` | 8080 | Spring Cloud Gateway Server Web MVC: routes as code (most specific first), request ids, stripping spoofable internal headers, per-client token-bucket rate limit on placing orders, retry + circuit breaker with a fast fallback for menus, no retries for payments |
| `mobile-bff` | 8090 | One endpoint per screen: the order-tracking card aggregates ordering, eta (API v2), catalog and dispatch in parallel on virtual threads, degrades per part, and formats for the app (IST times, ₹ with Indian grouping) |

```bash
cd code
mvn -B -pl chapters/ch10-api-gateway-bff/gateway,chapters/ch10-api-gateway-bff/mobile-bff -am verify

mvn -pl chapters/ch10-api-gateway-bff/gateway spring-boot:run
mvn -pl chapters/ch10-api-gateway-bff/mobile-bff spring-boot:run

curl -s localhost:8080/bff/mobile/v1/orders/o-1/tracking | jq          # through the gateway
for i in 1 2 3 4 5 6; do curl -s -o /dev/null -w '%{http_code} ' -X POST localhost:8080/api/orders -d '{}' -H 'Content-Type: application/json'; done
# 200 200 200 200 200 429   (with ordering running; 502/503 for the first five if it isn't)
```
