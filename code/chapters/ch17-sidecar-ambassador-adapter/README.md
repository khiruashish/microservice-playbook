# Chapter 17 — Sidecar, Ambassador & Adapter

Three helper processes that run beside kitchen-service in its pod, each a pattern from the chapter:

| Package | Pattern | What it does |
|---|---|---|
| `ambassador/` | Ambassador | The app calls `http://localhost:15001`; the ambassador adds the payment provider's token (re-read when the secret rotates), applies a per-try timeout, retries GET/HEAD and requests with an `Idempotency-Key`, opens a circuit breaker, and exposes counters |
| `adapter/` | Adapter | Prometheus scrapes `:9102/metrics`; the adapter translates the restaurant-tablet gateway's home-grown `/stats` page (names, units, labels, types) and reports `kitchen_tablet_gateway_up 0` when it can't reach it |
| `logship/` | Sidecar | Tails a log file on a shared volume and ships complete lines in batches; keeps its position across restarts, handles rotation and copy-truncate, retries when the sink is down |
| `fake/` | | A payment provider with configurable 503s and slow responses, and the tablet gateway |
| `experiment/` | | Real HTTP measurements on loopback |
| `k8s/kitchen-service.yaml` | | The pod: native sidecar containers (`initContainers` with `restartPolicy: Always`) |

Everything is plain JDK 25 (`com.sun.net.httpserver`, `java.net.http`, virtual threads).

## Run

```bash
cd code
mvn -B -pl chapters/ch17-sidecar-ambassador-adapter -am verify
java -cp chapters/ch17-sidecar-ambassador-adapter/target/classes dev.foodfleet.ch17.experiment.AmbassadorExperiment
```

## Verified output

Measured on a 2-vCPU Linux container; absolute latencies will differ on your machine, the shape won't.

```text
1. Provider with 10% 503s and 3% slow (800 ms) responses; 5,000 authorizations

path                                 success   auths made    p50 ms    p99 ms    max ms
direct (1 s timeout, no retry)        89.54%         4477       1.1     803.1     830.3
via ambassador (400 ms x 3 tries)     99.74%         4994       6.1     419.8     844.9

ambassador counters:
  ambassador_requests_total 5000
  ambassador_upstream_calls_total 5722
  ambassador_retries_total 722
  ambassador_failures_total 13
  ambassador_short_circuited_total 0

2. Healthy provider: what the extra hop costs (5,000 sequential calls, loopback)

path                                  p50 us    p99 us
direct                                   268      3224
via ambassador                           558      4930
added by the hop                         290      1706
```

`k8s/kitchen-service.yaml` validates against the Kubernetes 1.33 strict JSON schema
(`yannh/kubernetes-json-schema`, `v1.33.0-standalone-strict`), which also rejects misspelled fields.

Note on the JDK HTTP server: it writes headers and body separately, so without
`-Dsun.net.httpserver.nodelay=true` Nagle's algorithm and delayed ACKs add ~40 ms per response on a
kept-alive connection. Every class here that starts a server sets it.
