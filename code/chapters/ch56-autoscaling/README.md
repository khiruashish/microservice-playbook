# Chapter 56 — Autoscaling

Code and measurements for [chapter 56](../../../docs/deployment/ch56-autoscaling.md).

| Path | What |
|---|---|
| `src/main/java/.../hpa/` | `ReplicaCalculator` (the HPA's replica count for a CPU target: utilization of requests, the 10% tolerance, unready and missing pods as Kubernetes 1.37 treats them) and `ScalingBehavior` (stabilization windows, rate policies, min and max) |
| `src/main/resources/k8s/` | `deployment.yaml` (requests, no CPU limit, readiness that fails until warm, no `replicas`) and `hpa.yaml` (autoscaling/v2, 50% of requests, behavior written out) |
| `src/test/java/` | 6 tests |
| `verify/` | `build-libs.sh` (the menu-service image on chapter 54's runtime; Kubernetes and metrics-server source clones), `run.sh`, `check_apis.sh` (12 checks), `tools/` (`ScaleLab`, `Measure`) |

```bash
K3S_DISABLE=traefik ../../ch52-health-checks-actuator/verify/k3s-lab.sh   # a single-node cluster with metrics-server
JAVA_HOME=/path/to/jdk-25 verify/run.sh                                   # about 45 minutes
```

## Findings (k3s v1.37.1+k3s1, one 2-vCPU node, metrics-server 0.9.0 at 15 s, JDK 25.0.4, two runs)

Every request is 20 ms of CPU; pods request 200m; the HPA targets 50%, so one pod per 100m of load.

**step**: 1 pod, 30 requests/s (600m) from 30 s to 270 s.

| | Run 1 | Run 2 |
|---|---|---|
| First scale-up after the load started | 29 s (1 → 5) | 34 s (1 → 5) |
| Peak | 7 pods after 94 s | 8 pods after 77 s |
| First pod removed after the load stopped | 301 s | 167 s |
| Back to 1 pod | 333 s | 331 s |
| Failed requests | 181, all 60–80 s | 150, all 60–80 s |
| p99, first minute / minutes 2–4 | 6,241 / 80 ms | 5,375 / 39 ms |

**warmup**: min 2, 20 requests/s (4 pods' worth); new pods burn a CPU for 30 s.

- Readiness failing until warm: peak 5 pods; p99 219–281 ms in the first two minutes; 0 failed.
- Ready at once: peak 8 (the maximum) within a minute, held for the rest of the run; p99 365–421 ms; 0 failed.

**flap**: 24 requests/s (480m) for 60 s, off for 60 s, four times; pods warm for 10 s.

- Default 300 s scale-down window: 2 scaling changes, 5 pods started, 97–192 failed, all in the first on-phase.
- No window: 10–11 changes, 17–18 pods started, 325–432 failed, mostly in later on-phases.

**apis**: 12 of 12 found in Kubernetes 1.37.1 and metrics-server 0.9.0.

## Notes

- All pods share one 2-vCPU node, so starting JVMs compete with serving ones (CPU shares follow requests). Failed requests cluster in the 20 s after each scale-up. On a multi-node cluster new pods usually land elsewhere; the timings don't depend on this.
- A trial run before these two gave the same replica counts, with 24 failures for the default window in `flap` and a p99 of 1,065 ms for ready-at-once pods in `warmup`. The tables report the two full runs.
- `ScaleLab`'s warm-up burns CPU time with one thread, a stand-in for a JVM's class loading and JIT compilation. Real warm-up is shorter or longer depending on the service; chapter 60 shortens it.
