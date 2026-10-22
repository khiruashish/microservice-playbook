# Chapter 52 — Health Checks & Actuator Hardening

Code and measurements for [chapter 52](../../../docs/observability/ch52-health-checks-actuator.md).

| Path | What |
|---|---|
| `src/main/java/.../health/` | `Status` (Spring Boot's order and HTTP mapping), `HealthCheck`, `HealthGroup` (worst member wins, checks in parallel with a timeout), `CachedCheck` |
| `src/main/java/.../availability/` | `Availability`: liveness and readiness as the application's own state |
| `src/main/java/.../shutdown/` | `GracefulShutdown`: refuse readiness, drain, finish in-flight requests |
| `src/main/java/.../pg/` | `PgPing`: `SELECT 1` over the PostgreSQL wire protocol (trust authentication; for the lab) |
| `src/main/resources/` | `application.yml` (probe groups, management port, endpoint access), `k8s/deployment.yaml` (startup, liveness and readiness probes, `preStop` sleep), `k8s/networkpolicy.yaml` |
| `src/test/java/` | 12 tests |
| `verify/` | `k3s-lab.sh` (a single-node k3s 1.37.1), `build-libs.sh` (a jlink'd Java 25 runtime image built from scratch; Boot and Kubernetes source clones), `run.sh`, `check_apis.sh` (20 checks), `tools/` (`OrderService`, `Measure`, `Fetch`) |

```bash
verify/k3s-lab.sh            # as root; K3S_SANDBOX=1 only in restricted VMs (see the script)
verify/run.sh                # all four experiments and the API checks: about 35 minutes
verify/run.sh shutdown       # one experiment
```

## Findings (k3s v1.37.1+k3s1, one 2-vCPU node, JDK 25.0.4, PostgreSQL 16 outside the cluster, two runs)

**dependency**: 3 replicas; PostgreSQL stopped for 60 s; 20 requests/s per stream. Success during the outage and after:

| Probe design | /menu keep-alive | /menu new connections | /orders | Pods | /orders back after |
|---|---|---|---|---|---|
| liveness = /actuator/health | 43.8–47.1% | 38.6–41.5%, then 83.2–85.3% after | 0.2%, then 81.8–100% | 6 restarts | 14–95 s |
| readiness includes the database | 100% | 22.8–24.1%, then 89.5–91.2% after | 0–0.2%, then 90.1–100% | 0 ready, 0 restarts | 1–41 s |
| neither (Boot's groups) | 100% | 100% | 0.2–0.3% (fast 503), then 100% | 3 ready | immediately |

In every configuration, 3–4 of about 600 /orders requests also failed in the 30 s before the outage. This is the lab's 800 ms database check on new connections, the same in all three designs.

**startup**: 40 s start, observed for 180 s.

- Liveness with a 10 s initial delay and no startup probe: never ready, 4–5 restarts, ending in CrashLoopBackOff.
- Liveness with a 60 s initial delay: ready at 43–44 s.
- A startup probe: ready at 43–44 s, with 0 restarts.

**shutdown**: rolling restart under 4 × 50 requests/s.

- Stop at once: 92–96 requests failed.
- Graceful shutdown only: 79–96 failed.
- Graceful shutdown plus readiness refusing for a 5 s drain: 0–2 failed.
- Graceful shutdown plus a `preStop` sleep of 5 s: 0 failed.

**actuator**:

- A heap dump (4.6 MB, taken in 342–388 ms) contained the database password twice.
- A NetworkPolicy allowing 8081 only from the `monitoring` namespace:
  - From `default`, the management port was refused and 8080 still answered.
  - From `monitoring`, both answered.
  - The pod stayed ready (the probes come from the node).

**apis**: 20 of 20 found in Spring Boot 4.1.1 and Kubernetes 1.37.1.

## Notes

- Spring Boot can't be built without Maven Central, so `OrderService` implements Boot's health model, and `check_apis.sh` checks it against Boot's source.
- `STARTUP_SECONDS` is a sleep before the ports open. It stands in for a Spring Boot context refresh.
- The images are built from scratch: a jlink'd runtime plus the host's glibc. No base image is pulled.
