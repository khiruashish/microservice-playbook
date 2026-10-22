---
title: "AsyncAPI"
description: "Event APIs described with AsyncAPI 3.0 at FoodFleet (ADR-027): one document per topic in the producing service's repository, listing consumers, validated against the official schema in CI, with event schemas checked by the schema registry in FULL_TRANSITIVE mode so any consumer can read any event ever written and deploys need no order. Why events need it more than HTTP: topics are read long after they are written, by consumers the producer may not know."
progress: done
---

# AsyncAPI

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | A specification for event-driven APIs: servers (brokers), **channels** (topics), **operations** (send, receive) and **messages** with payload schemas and headers; version 3.0 separates channels from operations |
| **FoodFleet uses** | An AsyncAPI 3.0 document for every topic, in the producer's repository, with `x-consumers`; schema registry compatibility `FULL_TRANSITIVE` for domain events |
| **Decided in** | [ADR-027](../communication/ch27-api-contracts.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 27](../communication/ch27-api-contracts.md), [chapter 19](../architecture-patterns/ch19-event-driven-architecture.md) |
| **Related** | [OpenAPI](openapi-springdoc.md), [Kafka](kafka.md), [Contract testing](contract-testing-tools.md), [Backstage](backstage.md) |

## A topic, documented

```yaml title="order-events.asyncapi.yaml (chapter 27)"
--8<-- "code/chapters/ch27-api-contracts/src/main/resources/static/contracts/order-events.asyncapi.yaml"
```

Chapter 27's build validates it against the AsyncAPI 3.0.0 JSON schema (valid), as it validates the OpenAPI document.

## Compatibility is the schema registry's job

The document describes; a schema registry (Confluent, Apicurio; Buf for protobuf) enforces, before producers can use a new version:

| Mode | New schema can… | Allowed changes (Avro) | Deploy first |
|---|---|---|---|
| `BACKWARD` (default) | read data written with the previous schema | Delete fields; add optional fields | Consumers |
| `FORWARD` | have its data read by the previous schema | Add fields; delete optional fields | Producers |
| `FULL` | both | Add or delete optional fields | Either |
| `*_TRANSITIVE` | the same, against **all** earlier versions | | |

Topics are read long after they are written: replays, new consumers, retention. FoodFleet uses **`FULL_TRANSITIVE`** for domain events, so any consumer can read any event ever written, and producers and consumers deploy in any order.

## Why events need it more

- **Consumers are invisible to the producer**: a topic has no access log of callers. `x-consumers` and the catalog are how a producer knows whom a change affects ([Backstage](backstage.md)).
- **Old events live on**: a breaking change breaks replays, not just today's consumers.
- **Meaning changes don't show in schemas**: as with HTTP, add a new field rather than changing what one means ([chapter 27](../communication/ch27-api-contracts.md)'s `etaMinutes` change broke two consumers with no schema diff).
- **Tolerant readers on the consumer side**: unknown fields ignored, unknown enum values to a default.

## Tooling

| Need | Tool |
|---|---|
| Validate documents | AsyncAPI CLI (`asyncapi validate`), or the JSON schema in a test |
| Docs and catalog | AsyncAPI generator (HTML), Backstage API entities of type `asyncapi` |
| Code from documents | AsyncAPI generator templates; Springwolf to generate documents from Spring code |
| Contract tests for messages | Pact message pacts, Spring Cloud Contract messaging |

## Pitfalls

- **Documents without enforcement**: the registry, not the document, stops a bad schema.
- **`BACKWARD` (non-transitive) on long-retention topics**: compatible with the last version, not the first.
- **No headers in the description**: trace context, event ids and versions travel there.
- **One document for a whole broker**: keep it per producing service, next to the code.
