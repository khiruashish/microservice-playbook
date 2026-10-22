---
title: "APM: Datadog, New Relic, Dynatrace"
description: "Commercial application performance monitoring against FoodFleet's OpenTelemetry-first stack: what APM suites bundle (agents, traces, metrics, logs, profiling, RUM, AI-assisted root cause), where they save effort, and the questions chapter 47 says to ask of any of them: what the agent costs (the OTel Java agent added 38–56% CPU and 13–15 s of startup on thin services), what it sends (a JVM command-line token ended up in telemetry), how sampling keeps the slow traces, and how pricing grows with hosts, spans and series. FoodFleet exports OTLP so the backend can change in the collector."
progress: done
---

# APM: Datadog, New Relic, Dynatrace

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | Commercial observability suites: a language agent per service, plus hosted traces, metrics, logs, profiles, real-user and synthetic monitoring, dashboards, alerting and automated analysis |
| **FoodFleet uses** | **None as the primary stack.** Services emit OpenTelemetry (OTLP) to its own collector ([ADR-047](../observability/ch47-observability-fundamentals.md#8-enterprise-case-study)); any vendor that accepts OTLP can sit behind the collector |
| **Learn it in** | [Chapter 47](../observability/ch47-observability-fundamentals.md), [chapter 70](../performance-scalability/ch70-finops.md) (cost) |
| **Related** | [OTel Collector & LGTM](otel-collector-lgtm.md), [Distributed tracing](distributed-tracing.md), [Profiling](profiling.md) |

## What a suite bundles

| | Datadog | New Relic | Dynatrace |
|---|---|---|---|
| Java instrumentation | `dd-java-agent` (or OTel) | New Relic Java agent (or OTel) | OneAgent, injected per host |
| OTLP ingest | Yes (Agent or direct) | Yes (native OTLP endpoint) | Yes |
| Signals | APM, infra, logs, profiling, RUM, synthetics, security | Same breadth, query language NRQL | Same, with automatic topology (Smartscape) and causal analysis (Davis) |
| Pricing grows with | Hosts, ingested spans and logs, custom metrics | Users and ingested data | Host units / consumption |

Pricing models change often; model them on your own volumes before choosing.

## Where an APM saves effort

- **No backend to run**: storage, scaling and upgrades for traces, metrics and logs are the vendor's.
- **Automatic instrumentation** and service maps on day one.
- **Correlation built in**: trace → logs → host → profile in one UI.
- **RUM and synthetics** tied to backend traces.

## Questions to ask of any of them

[Chapter 47](../observability/ch47-observability-fundamentals.md) measured the costs that every agent-based approach shares:

| Question | What chapter 47 found with the OpenTelemetry Java agent |
|---|---|
| What does the agent cost? | **+38–56% CPU** per order on thin services (about 0.3 ms per span), **13–15 s more startup**, memory 394 MB → 740–770 MB for three JVMs |
| What does it send? | The JVM command line on every batch, **including a token passed as a system property** |
| Does sampling keep what matters? | 1% head sampling kept 2–4 of 248–285 slow traces; tail sampling all of them |
| What does each signal cost? | Spans 2.6 KB per order; metrics ~500 KB a minute regardless of traffic; one high-cardinality label multiplied series 1,000× |
| Can we leave? | Vendor agents and proprietary APIs in code make switching a rewrite; OTLP and the OpenTelemetry API don't |

## FoodFleet's position

- **Instrument with OpenTelemetry** (Spring Boot's Micrometer Tracing/OTel support, the OTel agent where needed), never with a vendor API in business code.
- **Export OTLP to the collector**; the backend is an exporter in its config. Sending a copy to a vendor during an evaluation is one more exporter.
- **Decide sampling, trimming and masking in the collector**, before data leaves.
- **Budget telemetry like any other cost**: per-service ingest is a FinOps line ([chapter 70](../performance-scalability/ch70-finops.md)).

## Pitfalls

- **Two agents in one JVM** (vendor and OTel): double instrumentation, conflicting class transformations.
- **Custom metrics with IDs as tags**: the bill grows with cardinality.
- **Logs ingested by default at full volume**.
- **Trusting automated root cause** without the fields that explain a failure: wide events and span attributes still have to be added.
