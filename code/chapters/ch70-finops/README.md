# Chapter 70 — Cost Optimization (FinOps)

Code and measurements for [chapter 70](../../../docs/performance-scalability/ch70-finops.md).

| Path | What |
|---|---|
| `src/main/java/.../app/` | `MenuApi`: `api` (renders a menu as JSON, `RENDER` times over), `cache` (`CACHE_MB` of menus in memory), `batch` (every core busy `BUSY_S` of every `PERIOD_S`); readiness and graceful shutdown |
| `src/main/java/.../rightsize/` | `Recommender`: VPA 1.8.0's recommendation rules (p90 + 15%, memory from interval peaks, minimums 25m and 250 MiB, bounds at p50 and p95) |
| `src/main/java/.../cost/` | `Fleet`: OpenCost's default prices, first-fit-decreasing packing, showback by request, usage and max(request, usage) |
| `src/main/resources/k8s/menu.yaml` | `menu-on-demand` and `menu-spot` Deployments behind one Service |
| `src/test/java/` | `UnitTest` (4, tag `unit`) |
| `verify/` | `build-libs.sh` (image `foodfleet/finops:ch70`), `run.sh`, `check_apis.sh` (14 checks), `fleet.tsv` (FoodFleet's 24 services as declared), `tools/` (`Lab`, `RunSuites`) |

```bash
JAVA_HOME=/path/to/jdk-25 verify/run.sh   # about 30 minutes; needs Docker and a cluster
```

## Findings (JDK 25.0.4, one 2-vCPU VM, Docker and k3s 1.37.1, two runs)

**usage** (a compressed day: 24 "hours" of 5 s; sampled every second):

| Workload | Declared | CPU mean | CPU p90 | CPU max | Mem peak | VPA CPU | VPA memory | CPU used | Mem used |
|---|---|---|---|---|---|---|---|---|---|
| api | 1 CPU, 2 GiB | 0.09–0.10 | 0.16–0.17 | 0.43–0.58 | 106–109 MiB | 0.18–0.20 | 250 MiB | 9–10% | 5% |
| cache | 1 CPU, 4 GiB | 0.03–0.04 | 0.06–0.07 | 0.27–0.31 | 892–910 MiB | 0.07–0.08 | 1,025–1,046 MiB | 3–4% | 21% |
| batch | 2 CPU, 4 GiB | 0.37 | 1.84–1.85 | 1.89–1.90 | 838–871 MiB | 2.12–2.13 | 963–1,002 MiB | 18% | 20–21% |

The api at each size: declared p99 5.1 ms; VPA target as limits (0.2 CPU, 256 MiB) p99 78–204 ms; VPA memory and no CPU limit p99 4.6–5.3 ms; 128 MiB and no CPU limit p99 5.8–7.0 ms. No failures.

**fleet** (80 pods on 4 vCPU / 16 GiB nodes; OpenCost's default prices): declared 97 CPUs, 240 GiB, 32 nodes, $4,537 a month on demand ($955 spot); right-sized 29.6–30.5 CPUs, 44.3–45.0 GiB, 8–9 nodes, $1,134–1,276 ($239–269 spot). Showback: idle $1,557 (34%) by requests or by max(request, usage), $4,219–4,225 (93%) by usage.

**spot** (6 copies of 0.25 CPU; one copy 54–58 requests/s; load 194–209 requests/s; spot reclaimed 30–90 s):

| On demand + spot | $/hour | Served (120 s) | Served while reclaimed |
|---|---|---|---|
| 6 + 0 (no reclaim) | 0.0538 | 99.6% | 100% |
| 0 + 6 | 0.0113 | 44–49% | 0–1% |
| 2 + 4 | 0.0255 | 71–72% | 42–49% |
| 3 + 3 | 0.0325 | 76–78% | 55–61% |

**tests**: unit 4/4. **apis**: 14 of 14 found in VPA 1.8.0 and OpenCost 2.5.3.

## Notes

- Usage is read from the container's main process in `/proc` (CPU time, resident memory), close to the kubelet's usage and working set. The day is compressed 720×, so VPA's 24-hour memory interval becomes the whole day: one peak.
- The fleet's requests, replicas and teams in `fleet.tsv` are FoodFleet's (illustrative); each kind is scaled by its measured VPA target over its declaration.
- Prices are OpenCost's default list ("based on GCP us-central1"); the totals are illustrative, the ratios hold.
- The spot experiment's VM also ran the load generator and kube-proxy, so the load was kept to about 200 requests a second; `RENDER=240` makes each request about 5 ms of CPU. Requests used a new connection each, so the Service balanced every request (chapter 68).
