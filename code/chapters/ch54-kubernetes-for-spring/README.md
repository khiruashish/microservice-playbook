# Chapter 54 — Kubernetes for Spring Developers

Code and measurements for [chapter 54](../../../docs/deployment/ch54-kubernetes-for-spring.md).

| Path | What |
|---|---|
| `src/main/java/.../config/` | `MountedConfig`: a ConfigMap or Secret directory, read once (like `configtree:`) or watched across the kubelet's `..data` symlink swaps |
| `src/main/java/.../runtime/` | `PodInfo`: the Downward API's pod, namespace, node and limits; CPU-bound thread counts from the limit |
| `src/main/resources/` | `k8s/deployment.yaml` (Deployment with requests, memory limit, no CPU limit, Downward API, mounted config, security context, probes; Service; PodDisruptionBudget; ConfigMap), `logback.xml` |
| `src/test/java/` | 7 tests |
| `verify/` | `build-libs.sh` (menu-service's image on a jlink'd runtime, using chapter 53's libraries; Kubernetes source clone), `run.sh`, `check_apis.sh` (8 checks), `tools/` (`KubeLab`, `Measure`) |

```bash
../../ch52-health-checks-actuator/verify/k3s-lab.sh      # a single-node cluster, if needed
JAVA_HOME=/path/to/jdk-25 verify/run.sh                  # about 20 minutes; the disruption experiment drains the node
```

## Findings (k3s v1.37.1+k3s1, one 2-vCPU node, cgroup v1, JDK 25.0.4, two runs)

**cpu**: medians of 3 starts, then 20 requests/s of 5 ms CPU work for 30 s.

| Resources | Ready after | JVM started in | p99 | Throttled |
|---|---|---|---|---|
| limit 250m | 6.2–6.5 s | 3,900–4,007 ms | 93–97 ms | 927–2,239 ms |
| limit 500m | 3.5–4.2 s | 1,802–1,838 ms | 16–39 ms | 33–156 ms |
| limit 1000m | 2.9–3.1 s | 939–950 ms | 11 ms | 0 |
| request 250m, no limit (the JVM sees 2 CPUs) | 2.2–2.7 s | 723–797 ms | 10–16 ms | 0 |

**config**:

- After a ConfigMap patch, a watched mounted directory saw the new value after 1.4–24 s in run 1 and 67–76 s in run 2.
- An env var, `configtree:`-style read-once values and a `subPath` file never updated, until a rollout restart.

**scaling**: 1 → 3 replicas under 50 requests/s.

- New connections spread across all three pods within 10 s of the new pods being ready (at 13.8–14.2 s).
- The keep-alive pool sent 250 of 250 requests per window to pod 1 for 50 s.

**disruption**: `kubectl drain` of the only node.

- Without a PDB: all 3 pods evicted in 2 s, 295–331 requests failed, and all pods were ready again 5–6 s after uncordon.
- With `minAvailable: 2`: 1 pod evicted and the rest refused by the eviction API, 0–1 requests failed. The drain gave up at its 60 s timeout.

**apis**: 8 of 8 found in Kubernetes 1.37.1.

## Notes

- In the runs, the drain summary line said "drain finished" for the PDB case even though the drain had timed out: its check matched "pending nodes to be drained". `Measure` now reports "gave up (timeout)" for that case.
- `/work` runs on platform threads, because per-thread CPU time isn't available for virtual threads.
