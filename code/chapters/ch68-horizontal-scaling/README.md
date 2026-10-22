# Chapter 68 — Horizontal Scaling & Partitioning

Code and measurements for [chapter 68](../../../docs/performance-scalability/ch68-horizontal-scaling.md).

| Path | What |
|---|---|
| `src/main/java/.../app/` | `Main`: order-service (carts in memory or PostgreSQL, `GET /work` with about 5 ms of CPU and an optional ledger call, `X-Pod` on every answer) and the ledger (2 calls at a time, 40 ms each: at most 50 a second); `Carts` |
| `src/main/java/.../partition/` | `Placement` (hash mod n, consistent hashing with virtual nodes, rendezvous, jump consistent hash), `Analysis` (keys moved, spread, Zipf) |
| `src/main/resources/k8s/scaling.yaml` | PostgreSQL, the ledger and order-service (0.25 CPU per copy) with its Service |
| `src/test/java/` | `UnitTest` (5, tag `unit`) |
| `verify/` | `build-libs.sh` (image `foodfleet/scale:ch68`), `run.sh`, `check_apis.sh` (8 checks), `tools/` (`Lab`, `RunSuites`) |

```bash
JAVA_HOME=/path/to/jdk-25 verify/run.sh   # about 15 minutes; needs docker and a cluster (KUBECTL, LOAD_IMAGE)
```

## Findings (k3s 1.37.1 on one 2-vCPU VM, JDK 25.0.4, two runs)

**sessions** (3 copies; 200 users × 5 adds, then a read; a new connection per request):

| Carts | Affinity | Event | Intact | Per copy |
|---|---|---|---|---|
| memory | none | | 0–2 of 200 | 31–37% |
| memory | ClientIP | | 200 of 200 | 100% / 0% / 0% |
| memory | ClientIP | busiest copy replaced | 0 of 200 | 100% / 0% / 0% |
| PostgreSQL | none | | 200 of 200 | 31–35% |
| PostgreSQL | none | busiest copy replaced | 200 of 200 | 32–36% |

**connections** (8 at a time, 4,000 per row): keep-alive over 4 copies: run 1 0/33/40/27%, run 2 51/0/0/49%; a new connection per request 24–26% each. After scaling 2 → 4 with keep-alive: run 1 100/0/0/0%, run 2 45/55/0/0% (new copies last); with new connections 23–27% each.

**scaleout** (0.25 CPU per copy; 32 in flight; 20 s): alone 35, 62–66 and 121–132 requests/s for 1, 2 and 4 copies (p50 889–900, 389–504, 169–198 ms); behind the 50/s ledger 26, 43–45 and 50 (12–13 per copy at 4).

**partitioning** (100,000 order IDs; identical in both runs except lookup time):

| Placement | 4 → 5 | 10 → 11 | 10 → 9 | Busiest/mean (10 nodes) | Lookup |
|---|---|---|---|---|---|
| hash mod n | 79.8% | 90.7% | 89.9% | 1.01 | 58–73 ns |
| ring, 1 point per node | 1.9% | 7.5% | 17.9% | 2.26 | 129–139 ns |
| ring, 16 points | 17.5% | 9.6% | 7.2% | 1.40 | 95–118 ns |
| ring, 256 points | 20.0% | 8.0% | 9.4% | 1.10 | 136–144 ns |
| rendezvous | 20.1% | 9.1% | 9.9% | 1.01 | 71–80 ns |
| jump | 20.0% | 9.1% | 9.9% | 1.02 | 87–91 ns |

Hot keys (1,000,000 orders, 2,000 restaurants, Zipf; 12 partitions): busiest partition by restaurant 12.9% / 19.0% / 27.6% for s = 0.8 / 1.0 / 1.2; by order ID 8.4%; restaurant with the top 10 salted over 4 sub-keys 10.0% / 12.2% / 15.7%.

**tests**: unit 5/5. **apis**: 8 of 8 found in Kubernetes 1.37.1, JDK 25.0.4, Kafka 4.2.2, Cassandra 5.0.9 and Spring Session 4.1.1.

## Notes

- Requests that should be balanced individually are sent with `Connection: close`, which the JDK client allows only with `-Djdk.httpclient.allowRestrictedHeaders=connection`. order-service echoes `Connection: close` in its answer; without that, the JDK client returned the connection to its pool and the next request failed with "header parser received no bytes".
- Virtual threads can't read their own CPU time (`getCurrentThreadCpuTime` returns -1 for them), so `/work` spins a loop calibrated at startup on a platform thread. Measured outside the cluster, `/work` used 6.9–8.8 ms of CPU per request on a new connection, `/ready` 1.9 ms.
- The first keep-alive row's idle copies may also reflect connections opened just as the Service's endpoints were being programmed; either way, kube-proxy never moves an open connection.
- The 1-copy p99 varied from 1.8 s to 7.0 s between runs: a 0.25-CPU JVM compiles slowly and is throttled in 100 ms periods. Throughput and medians were stable.
