---
title: "Lab 20 — Chaos Game Day"
description: "Plan and run FoodFleet's checkout game day: a hypothesis per fault in SLO terms, a blast radius of one cell, an abort guard, and roles. Before the day, ask the real alert rules which faults will page: a 10-minute payment outage paged within a minute and kept paging 29 minutes after it ended; 5% failures for 30 minutes paged at minute 17; 1% for 30 minutes and 0.3% for six hours never paged at all. Rehearse on a laptop (a timeout with a fallback kept 100% of orders through a hung dependency; a timeout with a retry kept 16%), then run it as a Chaos Mesh workflow validated against Chaos Mesh 2.8.4. About 90 minutes."
progress: done
---

# Lab 20 — Chaos Game Day

<div class="badges" markdown><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **You'll build** | A game day for the checkout journey: the plan, the paging forecast, a rehearsal, and the experiments as a Chaos Mesh workflow |
| **Time** | About 90 minutes, plus the day itself |
| **Builds on** | [Lab 06](lab-06-resilience.md) (timeouts and fallbacks), [lab 12](lab-12-observability.md) (telemetry) and [lab 15](lab-15-kubernetes-helm-keda.md) (the cluster) |
| **Chapters** | [45 (chaos engineering)](../resilience/ch45-chaos-engineering.md), [51 (SLOs and alerting)](../observability/ch51-slo-alerting.md), [42 (failure modes)](../resilience/ch42-failure-modes.md), [65 (chaos tests)](../testing/ch65-performance-chaos-arch-tests.md) |
| **Code** | `code/code-lab/lab-20-chaos-game-day/` |
| **Needs** | `promtool` and Python 3 with PyYAML; JDK 25; a cluster with Chaos Mesh for the day itself |

!!! note "About the lab code"
    The preparation **was run**: `paging.py` evaluated FoodFleet's real alert rules (`code/foodfleet/slo/foodfleet-slo.rules.yml`, generated from the platform model) with promtool 3.15.0 over three days of synthetic checkout traffic per fault; `experiments.sh` ran [chapter 65](../testing/ch65-performance-chaos-arch-tests.md)'s chaos experiment with JDK 25.0.4 (k3s was running on the same VM; the results matched the chapter's); and the workflow passed a server-side dry run on k3s v1.37.1 with Chaos Mesh 2.8.4's CRDs. Chaos Mesh's controller wasn't installed, so the workflow itself hasn't run.

## 1. The plan

A game day is an experiment with an audience: the team that owns checkout, the on-call engineer, and someone from product. FoodFleet runs one per critical journey a quarter, after the same experiments have passed weekly in staging ([ADR-045](../resilience/ch45-chaos-engineering.md#8-enterprise-case-study)).

| | Checkout game day |
|---|---|
| **Steady state** | Checkout ≥ 99.9% successful and p99 ≤ 300 ms, per minute, from the SLO dashboards ([ADR-051](../observability/ch51-slo-alerting.md#8-enterprise-case-study)) |
| **Hypotheses** | 1. payment-service slow on half its calls: checkout stays ≥ 99% successful; the latency SLO alert fires, the availability one doesn't. 2. A quarter of menu-service's pods killed: browse stays ≥ 99.9%; nobody is paged |
| **Blast radius** | The canary cell only, in the agreed hour, never during a lunch or dinner peak. ADR-045 starts every experiment at `mode: one`; these two have held three times in staging, so they may take a share of the cell |
| **Abort** | Automatic: the guard stops a fault after three failed checks (success < 97% in the cell). Manual: anyone in the room, any time, for any reason |
| **Roles** | Facilitator (runs the plan), operator (starts and stops faults), on-call (responds as if it were real), scribe (timeline), observer from product |
| **Before** | The forecast below; the rehearsal; rollback tested; support told; the incident channel open |
| **After** | A blameless review within two days: what we expected, what happened, and what we change |

## 2. Before the day: who will be paged?

A game day tests the alerts as much as the services. If the plan expects a page and none comes, is that a finding about the system or about the plan? Work it out beforehand. `paging.py` turns each planned fault into checkout traffic (20 orders a second, 0.02% errors normally, three days of history so every window is full) and asks promtool, minute by minute, what FoodFleet's real burn-rate alerts do:

```console
$ PROMTOOL=promtool python3 code/code-lab/lab-20-chaos-game-day/verify/paging.py promtool
checkout at 20 orders/s, 0.02% errors normally; FoodFleet's checkout SLO 99.9% over 30 days
minutes from the fault's start; each fault is followed by two hours of normal traffic
fault                                           for  budget spent                     page                   ticket
payment-service down                           10 m        23.1%        +1 min, until +39   +4 min, still at the end
payment latency: 5% of checkouts time out      30 m         3.5%       +17 min, until +33                    never
one bad pod of 10: 1% fail                     30 m         0.7%                    never                    never
a slow leak: 0.3% fail                        360 m         2.5%                    never                    never
```

What the forecast says about the plan:

- **A full outage pages within a minute**, and keeps paging for **29 minutes after it's fixed**: the slower of the two page conditions looks at the last 30 minutes. The on-call engineer will see the page still firing after recovery; say so in the plan, or the review will record it as a false alarm.
- **5% failures page at minute 17.** The fast-burn condition needs 1.44% errors over the last hour: 5% × 17 minutes ÷ 60 ≈ 1.4%. A 10-minute experiment at 5% would end before anyone was paged.
- **1% for 30 minutes never pages**, and spends 0.7% of the month's budget. That's the design (budget alerts page on what threatens the SLO), but it means hypothesis 1 can't be judged by "were we paged?". Judge it on the dashboard.
- **The six-hour leak at 0.3%** (three times the allowed rate) didn't even raise a ticket within eight hours: the ticket conditions look at one and three days. A leak like this is found by the error-budget dashboard or a review, not an alert. Chapter 51 chose these windows knowing that.

## 3. Rehearse on a laptop

The same faults, cheaply, before touching a cluster: `experiments.sh` runs chapter 65's experiment, in which order-service calls menu-service through a fault proxy, at 50 orders a second, with three versions of the client:

```console
$ code/code-lab/lab-20-chaos-game-day/verify/experiments.sh
fault                              order-service's client     fault aborted    orders OK during fault p99 during fault windows out of steady state
2 s latency on half the calls      no timeout                 no               1500 of 1500 (100%)    2016 ms          15 of 25
2 s latency on half the calls      300 ms timeout, 1 retry    after 7 s        323 of 400 (81%)       604 ms           4 of 25
2 s latency on half the calls      300 ms timeout, last price no               1500 of 1500 (100%)    303 ms           0 of 25
menu-service hangs                 no timeout                 after 7 s        50 of 400 (13%)        5000 ms          4 of 25
menu-service hangs                 300 ms timeout, 1 retry    after 7 s        65 of 400 (16%)        603 ms           4 of 25
menu-service hangs                 300 ms timeout, last price no               1500 of 1500 (100%)    303 ms           0 of 25
```

- **No timeout** survived slowness (every order succeeded, slowly) and failed completely when the dependency hung.
- **A timeout with a retry** made slowness *worse* than no timeout (81% against 100%): half the calls timed out, and half the retries landed on slow calls again.
- **A timeout with a fallback** (the last known price, [lab 06](lab-06-resilience.md)) kept every order through both faults. That's what hypothesis 1 assumes about payment's client; if the rehearsal with your real client looks like the middle row, fix the client before the game day, not during it.
- **The guard worked**: every run that fell below 90% success was stopped after 7 seconds, which is the point of an abort condition.

## 4. The day: the workflow

```yaml title="gameday/checkout-gameday.yaml"
--8<-- "code/code-lab/lab-20-chaos-game-day/gameday/checkout-gameday.yaml"
```

- **Each fault runs in parallel with its guard.** `abortWithStatusCheck` stops the workflow, and Chaos Mesh removes the fault, after three failed checks. The `slo-probe` answers from the same metrics as the SLO dashboards.
- **The selectors include `foodfleet.dev/cell: canary`.** Without that label, `mode: fixed-percent` picks from every cell.
- **The server checks every field.** Kubernetes validates the workflow against Chaos Mesh's schema; a misspelling such as `abortWithStatuscheck` is refused (*"strict decoding error: unknown field"*). A client that doesn't ask for strict validation would silently drop it, and the guard would never abort. Chapter 45 found exactly that with a broken copy of its own manifest.

```console
$ kubectl apply --dry-run=server -f code/code-lab/lab-20-chaos-game-day/gameday/checkout-gameday.yaml
workflow.chaos-mesh.org/checkout-gameday created (server dry run)
```

## 5. On the day

| Time | What happens |
|---|---|
| T−30 min | Facilitator confirms: steady state holds, rollback tested, support informed, nobody else deploying |
| T−5 min | Scribe starts the timeline; on-call has the runbook open |
| T | Operator applies the workflow; everyone watches the canary cell's dashboard |
| T+10 min | Experiment 1 ends (or the guard aborted it). Compare with the hypothesis and the forecast |
| T+15 min | Experiment 2 |
| T+20 min | Steady state again? If not, that's the most important finding of the day |
| T+2 days | Blameless review; each finding becomes a ticket with an owner, or an ADR change |

What makes it worth doing is the findings that weren't in the hypotheses. Chapter 45's weekly experiment found a client that had silently lost its read timeout and a circuit breaker that had never opened; FoodFleet's first unguarded experiment in production became an incident, and a typo kept a fault running for a whole weekend. Those three findings are why ADR-045 requires a guard, a deadline and strict validation on every experiment.

## Run it

```bash
PROMTOOL=promtool code/code-lab/lab-20-chaos-game-day/verify/run.sh       # forecast and rehearsal, ~10 min
# with a cluster: KUBECTL=kubectl CHAOS_MESH_CRDS=<chaos-mesh v2.8.4 manifests/crd.yaml> as well
```

## Check yourself

- [ ] You can explain why a 10-minute experiment at 5% errors wouldn't page anyone.
- [ ] You can say which client behaviour in the rehearsal you'd want before the real game day, and why the retry made things worse.
- [ ] Your game day plan has an abort condition someone other than the operator can trigger.

## Break it

!!! question "Exercises"
    1. Add a fault to `paging.py`: 2% errors for 60 minutes. Will it page? Work it out by hand first, then check.
    2. Lower the fast-burn condition's 5-minute window to 1 minute in a copy of the rules. What happens to the page for the 10-minute outage, and to how long it fires after recovery?
    3. Remove the cell label from the workflow's selectors. Which pods can experiment 2 now kill, and who would notice first?
    4. Write hypothesis 3 for a game day on the tracking journey (99.5% SLO): what fault, what steady state, what abort line, who's in the room?

**This is the last lab.** The [Code Lab index](index.md) lists them all; the [Components & Tools](../components-tools/index.md) section covers each tool used here in depth.
