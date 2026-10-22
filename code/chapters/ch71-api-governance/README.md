# Chapter 71 — API Governance & Developer Portals

Code and measurements for [chapter 71](../../../docs/governance-org/ch71-api-governance.md).

| Path | What |
|---|---|
| `src/main/java/.../lint/` | `Linter`: 10 rules from Spectral's recommended `spectral:oas` (same names and severities) and 10 FoodFleet house rules (most from Zalando's guidelines, numbers cited); `Baseline`: a ratchet keyed by rule and JSON pointer |
| `src/main/java/.../deprecation/` | `Deprecations`: Deprecation (RFC 9745), Sunset (RFC 8594) and Link headers from the OpenAPI document, a client that warns once, a usage log by `X-Client-Id` |
| `src/main/java/.../catalog/` | `Catalog`: Backstage's required fields and FoodFleet's checks (owners, lifecycles, API references, lint status, runbooks) |
| `src/main/resources/openapi/` | six OpenAPI 3.1 documents (menu, order, delivery, payment, rider, promotions) |
| `src/main/resources/catalog/` | 37 Backstage entities in JSON (which YAML accepts): 6 groups, 24 components, 7 APIs |
| `src/test/java/` | `UnitTest` (3, tag `unit`) |
| `verify/` | `build-libs.sh`, `run.sh`, `check_apis.sh` (26 checks), `violations.tsv` (33 planted problems), `tools/` (`Lab`, `RunSuites`) |

```bash
JAVA_HOME=/path/to/jdk-25 verify/run.sh   # under a minute
```

## Findings (JDK 25.0.4, two runs, identical apart from timings)

**lint**: 33 planted problems (7 that no rule describes). Caught by Spectral's rules 10, by house rules 16, missed 7 (exactly the 7 review-only ones), false findings 0. 13 errors and 13 warnings; failing on errors stops 4 of 6 documents. 0.56–0.67 ms per document.

| Document | Planted | Spectral | House | Missed |
|---|---|---|---|---|
| menu-service | 0 | 0 | 0 | 0 |
| order-service | 7 | 1 | 6 | 0 |
| delivery-service | 6 | 4 | 2 | 0 |
| payment-service | 9 | 2 | 4 | 3 |
| rider-service | 8 | 3 | 4 | 1 |
| promotions-service | 3 | 0 | 0 | 3 |

**ratchet**: order-service's 7 findings baselined; after the change 9 findings: 3 new (kebab-case and verb on `/orders/{orderId}/applyCoupon`, a float `discountAmount`), 1 fixed (`total`), 6 kept.

**deprecation**: `Deprecation: @1767571200`, `Sunset: Mon, 06 Jul 2026 00:00:00 GMT`, `Link: <…>; rel="deprecation"; type="text/html"`. 1,022 calls from 8 applications; callers of the deprecated operation: checkout-bff 140 (declared, warned), restaurant-portal 21 (declared, warned), support-console 14 and analytics-ingest 7 (neither declared nor warned: plain HTTP). The catalog declares 6 consumers; the log saw 8 callers.

**catalog**: 37 entities; 16 of 31 components and APIs pass every check. Failures: required fields 2, API definition 1, unknown owner 2, invalid lifecycle 1, unresolved providesApis 1, API/component owner mismatch 1, API document with lint errors 4, missing runbook 3.

**tests**: unit 3/3. **apis**: 26 of 26 found in Spectral's rulesets 1.16.0, Zalando's guidelines (commit 19a1905), Backstage 1.55.3 and Spring Framework 7.0.9.

## Notes

- Spectral itself wasn't run (npm wasn't reachable); `Linter` implements the same ten rules by name and severity, and `check_apis.sh` checks those names and severities in Spectral's source. The house rules are written as Java here; with Spectral they'd be a ruleset file extending `spectral:oas`.
- Java's `DateTimeFormatter.RFC_1123_DATE_TIME` writes single-digit days ("Mon, 6 Jul 2026"), which RFC 9110's IMF-fixdate doesn't allow; the gateway formats the Sunset date with a two-digit day. Spring Framework 7.0.9's handler uses `RFC_1123_DATE_TIME` (recipients generally accept it).
- The documents and catalog are generated once by a script and committed; the planted problems are listed in `violations.tsv` with the rule expected to catch each (or `-`).
