---
title: "Chaos Tools: Chaos Mesh, LitmusChaos, Toxiproxy"
description: "Chaos experiments at FoodFleet (ADR-045, ADR-065): steady-state hypotheses in SLO terms, an automatic abort guard, blast radius one pod until the hypothesis has held three times, Toxiproxy for TCP faults in CI and Chaos Mesh workflows in staging. Measured: a trickle and a black hole hung clients without timeouts; without a guard an experiment hurt half of all users for 30 s, with one 147–180 requests; a circuit breaker never opened on black-holed calls without a read timeout; an error-only hypothesis passed while p99 was 2 s; a misspelt duration made a fault permanent."
progress: done
---

# Chaos Tools: Chaos Mesh, LitmusChaos, Toxiproxy

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | **Toxiproxy**: a TCP proxy with "toxics" (latency, bandwidth, timeouts, resets, slicing) controlled over HTTP. **Chaos Mesh**: Kubernetes CRDs for network, pod, I/O, time and stress faults, with Workflows, Schedules and StatusCheck guards. **LitmusChaos**: ChaosEngines and experiments with probes, plus a hub of experiments |
| **FoodFleet uses** | Toxiproxy-based experiments in CI; Chaos Mesh workflows in staging; quarterly game days |
| **Decided in** | [ADR-045](../resilience/ch45-chaos-engineering.md#8-enterprise-case-study), [ADR-065](../testing/ch65-performance-chaos-arch-tests.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 45](../resilience/ch45-chaos-engineering.md), [chapter 65](../testing/ch65-performance-chaos-arch-tests.md), [lab 20](../code-lab/lab-20-chaos-game-day.md) |
| **Related** | [Resilience4j](resilience4j.md), [Load testing](load-testing-tools.md), [Monitoring](monitoring-prometheus-grafana.md) |

## Fault fidelity: real TCP faults

[Chapter 45](../resilience/ch45-chaos-engineering.md), Toxiproxy in front of restaurant-service:

| Fault | JDK `HttpClient`, no timeouts | …request timeout 1 s | Spring JDK factory, read timeout 1 s |
|---|---|---|---|
| Latency +2 s | OK, 2.0 s | timeout, 1.0 s | timeout, 1.0 s |
| **Bandwidth 1 KB/s (trickle)** | **hung** | **hung** | `IOException`, 1.0 s |
| **Black hole** | **hung** | timeout, 1.0 s | timeout, 1.0 s |
| TCP reset | `IOException`, 0.0 s | same | same |

Latency and errors are the easy faults. Trickles and black holes are what hang threads, and they need a proxy, not a stub.

## Guards and blast radius

| Experiment (+3 s latency, 30 s) | Verdict | Users hurt |
|---|---|---:|
| No guard, 1 of 2 instances | BROKEN, ran 30 s | **1,471–1,500** (49–50%) |
| **Guard: abort after 3 bad windows** | ABORTED at 3–4 s | **147–180** |
| Read timeout 150 ms + retry on the other instance | HELD | **0** |

| Circuit breaker under a black hole | Failing windows (of 20) | Breaker opened |
|---|---:|---|
| No read timeout | **19** | after the rollback |
| Read timeout 250 ms | 0 | 2.3–2.4 s |

A breaker counts outcomes; a call that never ends has none.

## Hypotheses need latency

[Chapter 65](../testing/ch65-performance-chaos-arch-tests.md), 2 s latency on half the calls to menu-service:

| order-service's client | Orders OK | p99 | Windows out of steady state (of 25) |
|---|---:|---:|---:|
| No timeout | **100%** | **2,011 ms** | **15** |
| 300 ms timeout, 1 retry | 81% (aborted) | 603 ms | 4 |
| **300 ms timeout, last known price** | 100% | 302 ms | **0** |

"No errors" passed while checkout took two seconds.

## Experiments as manifests

```yaml title="gameday/checkout-gameday.yaml (lab 20)"
--8<-- "code/code-lab/lab-20-chaos-game-day/gameday/checkout-gameday.yaml"
```

Chapter 45 validates every manifest against Chaos Mesh 2.8.4 and LitmusChaos 3.31.0 schemas, once strictly: `durations: 30s` was **valid** as written (the unknown field is pruned) and rejected only strictly. Without a duration, the fault stays until someone deletes it. [Lab 20](../code-lab/lab-20-chaos-game-day.md) dry-runs its workflow against the CRD and replays SLO burn-rate rules with promtool to know which alerts a game day should fire.

## Pitfalls

- **Experiments without an abort guard**.
- **Error-only hypotheses**.
- **Only latency and errors** as faults.
- **Manifests not validated strictly**; faults without a duration.
- **Production first**: FoodFleet runs in production only hypotheses that held in staging, in business hours, with on-call present.
