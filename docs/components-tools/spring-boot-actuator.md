---
title: "Spring Boot Actuator"
description: "Actuator as FoodFleet configures it (ADR-052): liveness and readiness on Spring Boot's probe groups only (no database), a startup probe, graceful shutdown with a preStop drain, and the management port 8081 with access denied by default, behind a NetworkPolicy. Measured on Kubernetes: a database check in liveness caused 6 restarts and 38–47% success during a 60 s outage; Boot's default groups 100%; a liveness probe shorter than startup gave CrashLoopBackOff; a preStop sleep took a rolling restart from 79–96 failures to 0; a heap dump held the database password twice."
progress: done
---

# Spring Boot Actuator

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span></div>

| | |
|---|---|
| **What it is** | Spring Boot's production endpoints: health (with liveness and readiness groups for Kubernetes), info, metrics and `/actuator/prometheus`, plus operational endpoints (env, loggers, heapdump, threaddump) that must be locked down |
| **FoodFleet uses** | `health`, `info` and `prometheus` only, read-only, on port 8081; probe groups for Kubernetes |
| **Decided in** | [ADR-052](../observability/ch52-health-checks-actuator.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 52](../observability/ch52-health-checks-actuator.md), [lab 01](../code-lab/lab-01-hello-microservice.md), [lab 15](../code-lab/lab-15-kubernetes-helm-keda.md) |
| **Related** | [Kubernetes](kubernetes.md), [Monitoring](monitoring-prometheus-grafana.md), [Spring Security](spring-security.md) |

## Configuration

```yaml title="application.yml (chapter 52)"
--8<-- "code/chapters/ch52-health-checks-actuator/src/main/resources/application.yml"
```

## Probes that help instead of hurt

[Chapter 52](../observability/ch52-health-checks-actuator.md), three replicas, PostgreSQL stopped for 60 s:

| Probe design | /menu (no database) during the outage | Pods |
|---|---:|---|
| Liveness = `/actuator/health` (includes the database) | **38.6–47.1%** | **6 restarts**, 0 of 3 ready at the lowest; /orders back 14–95 s after |
| Readiness includes the database | new connections **22.8–24.1%** | 0 restarts, **0 of 3 ready** |
| **Boot's default probe groups** | **100%** | 0 restarts, 3 of 3 ready; /orders failed fast with 503 |

Liveness asks "is this process broken beyond repair?"; readiness "should this pod get new traffic?". A shared dependency in either takes every pod out at once. It belongs in `/actuator/health` details and SLO alerts. A CI policy rejects Deployments whose liveness probe points at `/actuator/health`.

| A service that takes 40 s to start | Ready after | Restarts |
|---|---:|---:|
| Liveness, `initialDelaySeconds: 10` | **never** | 4–5, then CrashLoopBackOff |
| Liveness, `initialDelaySeconds: 60` | 43–44 s | 0 (but a minute blind after every restart) |
| **Startup probe** (up to 2 min) | 43–44 s | 0 |

## Shutting down cleanly

| Rolling restart, 50 requests/s per stream | Failed |
|---|---:|
| Stop at once on SIGTERM | 92–96 |
| Graceful shutdown only | 79–96 |
| **Graceful + `preStop: sleep: 5`** | **0** |

Graceful shutdown finishes in-flight requests, but new connections keep arriving until kube-proxy removes the pod. A 5 s `preStop` sleep covers that gap without code or a shell in the image:

```yaml title="deployment.yaml (chapter 52)"
--8<-- "code/chapters/ch52-health-checks-actuator/src/main/resources/k8s/deployment.yaml"
```

## An internal API

| Check | Result |
|---|---|
| Heap dump of order-service (what `/actuator/heapdump` returns) | **The database password appears 2 times** |
| Port 8081 from `default` namespace, with the NetworkPolicy | refused |
| Port 8081 from `monitoring` | 200 |
| Probes after the policy | ready, 0 restarts (the kubelet isn't a pod) |

```yaml title="networkpolicy.yaml (chapter 52)"
--8<-- "code/chapters/ch52-health-checks-actuator/src/main/resources/k8s/networkpolicy.yaml"
```

Spring Boot 4 makes every endpoint's access explicit; `heapdump` is inaccessible unless granted. FoodFleet never grants it and takes dumps with `jcmd` under change control.

## Metrics endpoint

`/actuator/prometheus` serves Micrometer's registry; ADR-048 excludes Actuator requests from request metrics and caps `uri` tags ([Monitoring](monitoring-prometheus-grafana.md)).

## Pitfalls

- **`/actuator/health` as the liveness probe**.
- **`exposure.include: "*"`**, or the management port reachable from the Ingress.
- **`env` with values, `heapdump`, `loggers` writable** without authorization.
- **Graceful shutdown longer than `terminationGracePeriodSeconds`**: the pod is killed mid-drain (FoodFleet: 20 s phase, 5 s preStop, 30 s grace).
- **Custom health indicators that call other services**: one slow dependency makes the probe time out.
