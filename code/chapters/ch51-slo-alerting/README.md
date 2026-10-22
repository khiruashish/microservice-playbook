# Chapter 51 — SLIs, SLOs, Error Budgets & Alerting

Code and measurements for [chapter 51](../../../docs/observability/ch51-slo-alerting.md).

| Path | What |
|---|---|
| `src/main/java/.../slo/` | `Slo` (objective, window, error budget), `BurnRateAlert` (the SRE Workbook's multi-window, multi-burn-rate conditions for any window), `ErrorBudget` (spent, remaining, time to exhaustion, policy) |
| `src/main/java/.../rules/` | `PrometheusSloRules` (recording rules for seven windows, budget remaining, page and ticket alerts, optional low-traffic guard) and `CheckoutSlos` (checkout availability 99.9% and latency 99% within 300 ms, over 30 days) |
| `src/main/resources/` | `prometheus/checkout-slo.rules.yml` (generated; a test keeps it in step), `alertmanager/alertmanager.yml` (page and ticket routes, inhibition), `application.yml` (a histogram bucket at 300 ms) |
| `src/test/java/` | 10 tests |
| `verify/` | `build-libs.sh` (promtool and Alertmanager 0.34.1 from GitHub releases, Micrometer via chapter 48's build, source clones for checks), `run.sh`, `check_apis.sh` (16 checks), `tools/` (`Measure`, `Latency`, `Routing`, `Json`) |

```bash
verify/run.sh                      # rules checks, all three experiments and the API checks: about 20 minutes
verify/run.sh latency routing      # the quick ones
```

## Findings (JDK 25.0.4, Prometheus 3.15.0's rule engine, Micrometer 1.17.1, Alertmanager 0.34.1, two runs)

The scenarios are deterministic and seeded; both runs gave identical results except Alertmanager's timing (±0.1 s).

**alerts**: checkout availability 99.9%/30 days at 1,000 requests/min, with three days of 0.02% failures before each incident.

| Incident (budget spent) | > 1% for 5m | > 0.1% for 5m | 14.4× over 1h only | multi-window page | multi-window ticket |
|---|---|---|---|---|---|
| outage 3 min (6.9%) | 6 min | 6 min | 1 min, stays 59 min after | 1 min, stays 29 min | — |
| 10% for 20 min (4.6%) | 6 min | 6 min | 9 min, stays 51 min | 9 min, stays 4 min | — |
| 2% for 2 h (5.5%) | 7 min | 6 min | 43 min | 43 min | — |
| 0.6% for 18 h (14.5%) | never | 6 min, 1,078 min firing | never | never | 9.9 h (8.0% spent) |
| 0.25% for 4 days (30.7%) | never | 7 min, 5,756 min firing | never | never | 25.1 h (8.0%) |
| 0.15% for 10 days (43.3%) | never | 8 min, 14,394 min firing | never | never | 44.3 h (8.0%) |
| 5% for 1 min × 6 (0.7%) | never | never | never | never | never |

False alarms on healthy services (p = 0.0005 per request):

- **1,000 requests/min for 7 days:** the "> 0.1%" alert fired 3 times; nothing else fired.
- **1 request/min for 30 days:** the 1h-only alert and the multi-window page each fired 16 times, once per failure (944 and 64 minutes of firing). The page with a 100-request minimum fired 0 times.

**latency**: 500,000 requests per distribution into Micrometer timers.

- The default buckets have none at 300 ms; the nearest are 268 and 358 ms. `histogram_fraction` read 99.14% for a true 99.41%, which makes the budget look 86% spent instead of 59%. The other misreadings were 96.90 for 96.96, 88.14 for 89.85, and 35.28 for 33.33.
- With `slo: 300ms` the `le="0.3"` bucket was exact in every case.
- With the `slo` bucket alone, `histogram_quantile` said 299–300 ms for everything.

**routing**:

- The page went to `/page` at 2.0–2.1 s, and the latency ticket to `/ticket` at 2.0–2.1 s.
- The availability ticket was inhibited until its page resolved, and then delivered at 8.0 s.

**apis**: 16 of 16 found in Prometheus 3.15.0, Alertmanager 0.34.1, Micrometer 1.17.1 and Spring Boot 4.1.1.

## Notes

- `promtool test rules` caps every query at 10,000 samples (a Prometheus server's default is 50 million). So the experiments leave out the 30-day budget rule and read alert timings in 12-hour chunks.
- Alertmanager runs without clustering (`--cluster.listen-address=`) so notifications aren't delayed while a cluster settles. Its storage is cleared first, so an earlier run's notification log doesn't suppress repeats.
