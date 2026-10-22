# Chapter 2 code — Characteristics & Principles (menu-service)

Docs: `docs/foundations/ch02-characteristics-principles.md`

One FoodFleet service, `menu-service` (port 8081), where each principle is visible in code and pinned by a test:

| Principle | Where | Test |
|---|---|---|
| Single responsibility, owned data | `catalog/`, `browse/MenuQueries` | `MenuControllerTest` |
| API-first, evolve without breaking (Boot 4 API versioning, Deprecation/Sunset) | `api/MenuController`, `application.yml` | `MenuControllerTest` |
| Tolerant reader | `pricing/PricingClient.SurgeResponse` | `RemoteSurgeLookupTest` |
| Design for failure: timeouts, `@Retryable`, `@ConcurrencyLimit` | `pricing/RemoteSurgeLookup` | `RemoteSurgeLookupTest` |
| Design for failure: graceful degradation | `pricing/SurgePricing` | `SurgePricingTest` |
| Principles as fitness functions | — | `ArchitectureTest` (ArchUnit) |

```bash
cd code
mvn -B -pl chapters/ch02-principles -am verify
mvn -pl chapters/ch02-principles spring-boot:run

curl -s localhost:8081/api/menus/rest-biryani-house -i                        # v1 (default) + Deprecation/Sunset
curl -s localhost:8081/api/menus/rest-biryani-house -H 'API-Version: 2.0'      # v2; pricing.source = BASE (no pricing-service running)
curl -s localhost:8081/api/menus/rest-biryani-house -H 'API-Version: 3.0' -i   # 400
```
