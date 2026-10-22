---
title: "OpenAPI & springdoc"
description: "FoodFleet's HTTP APIs are OpenAPI-first (ADR-027, ADR-071): an OpenAPI 3.1 document in each repository, reviewed like code, linted with Spectral plus house rules, diffed with oasdiff, and checked against the controllers by a drift test. Measured: Spectral's recommended rules caught 10 of 33 planted design problems, house rules 16 more, 7 needed a reviewer; a year of Order API changes broke consumers 11 times in 12, and tolerant readers removed four of those breaks. Where springdoc fits: generating a document from code to compare, or for code-first teams."
progress: done
---

# OpenAPI & springdoc

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span></div>

| | |
|---|---|
| **What it is** | **OpenAPI**: the standard description of an HTTP API (paths, operations, schemas, security), version 3.1 aligned with JSON Schema. **springdoc-openapi**: a library that generates an OpenAPI document and Swagger UI from Spring MVC/WebFlux controllers at runtime |
| **FoodFleet uses** | OpenAPI-first: the document is written and reviewed before code, lives in the repository, and the build checks the code against it |
| **Decided in** | [ADR-027](../communication/ch27-api-contracts.md#8-enterprise-case-study), [ADR-071](../governance-org/ch71-api-governance.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 27](../communication/ch27-api-contracts.md), [chapter 71](../governance-org/ch71-api-governance.md), [chapter 21](../communication/ch21-rest-api-design.md) |
| **Related** | [AsyncAPI](asyncapi.md), [Contract testing](contract-testing-tools.md), [Backstage](backstage.md), [API gateway](api-gateway.md) |

## The document is the contract

```yaml title="order-api.yaml (chapter 27)"
--8<-- "code/chapters/ch27-api-contracts/src/main/resources/static/contracts/order-api.yaml"
```

The document says what a schema can't on its own: `status` is an **open set** clients must tolerate, `riderName` is absent until assigned, and who the consumers are (`x-consumers`).

## Code and document can't drift

```java title="SpecDriftTest.java (chapter 27)"
--8<-- "code/chapters/ch27-api-contracts/src/test/java/dev/foodfleet/ch27/api/SpecDriftTest.java:16:47"
```

An endpoint added without the document, or a documented one that no longer exists, fails the build.

## What the checks catch

**Lint** ([chapter 71](../governance-org/ch71-api-governance.md)), 33 planted design problems in six documents:

| Caught by | Problems |
|---:|---|
| Spectral's recommended OpenAPI rules | 10 |
| FoodFleet's house rules (from Zalando's guidelines) | 16 |
| Nobody: a reviewer's checklist | 7 (no `Idempotency-Key` on a payment POST, a card number in a query string, errors as 200, a discount with no unit…) |
| False findings | 0 |

Legacy APIs get a ratchet: a change is judged only on the findings it adds (3 of 9 in chapter 71's example).

**Diff** ([chapter 27](../communication/ch27-api-contracts.md)), a year of twelve Order API changes: 11 broke a consumer. The structural diff flagged most, missed meaning changes (`etaMinutes` now counting to pickup, an empty 200 instead of 404) and flagged a removal nobody read. Tolerant readers (unknown fields ignored, unknown enum values mapped to a default) removed the breaks from additions. oasdiff runs on provider pull requests; ERRs need a reviewer's sign-off.

## Where springdoc fits

| Approach | How | When |
|---|---|---|
| **Design-first** (FoodFleet) | Write the document; drift test or code generation keeps code honest | Public and shared APIs; review happens on the contract |
| Code-first with springdoc | `springdoc-openapi-starter-webmvc-ui`; annotations (`@Operation`, `@Schema`) enrich it; `/v3/api-docs` serves it | Internal tools, prototypes |
| Both | Generate with springdoc in a test, diff against the committed document | Catch drift in detail (schemas, not only paths) |

Keep Swagger UI and `/v3/api-docs` off public endpoints in production, or behind authentication.

## Pitfalls

- **Documents written after the fact**, or generated and never reviewed.
- **Strict readers**: Jackson fails on unknown enum values by default; FoodFleet sets `read-unknown-enum-values-using-default-value`.
- **Meaning changes in place**: add a new field instead.
- **A diff gate without judgment**: ERRs that break nobody, and silence on meaning.
- **No consumer list**: removals break callers nobody knew about (chapter 71's access log found 2 undeclared callers of a deprecated operation).
