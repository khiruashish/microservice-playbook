# Chapter 45 — Chaos Engineering

Experiments that break FoodFleet on purpose, with a hypothesis, a limited blast radius and an automatic stop.

```text
src/main/java/dev/foodfleet/ch45/chaos/
├── Experiment    steady state → inject → observe each window → abort guard → rollback (always) → recovery check
├── Hypothesis    the steady state in SLO terms: success rate and p99 per window
├── Window        what users saw in one window, and the recorder that collects it
├── Toxic         Toxiproxy faults: latency, bandwidth, black hole, drop, reset, truncation; toxicity, direction
└── Toxiproxy     a minimal client for Toxiproxy's HTTP API
src/main/resources/
├── chaos-mesh/   NetworkChaos, PodChaos, a Workflow with an SLO StatusCheck that aborts, a weekly Schedule
└── litmus/       a ChaosEngine (pod-delete) with an HTTP probe that stops on failure and a Prometheus probe
src/test/java/…   10 tests
verify/
├── build-libs.sh          Toxiproxy 2.12.0 binary; Resilience4j 2.4.0 and Spring Framework 7.0.9 client classes from their tags
├── run.sh                 fault matrix, blast radius and the guard, breaker hypothesis, manifest validation; then check_apis.sh
├── validate_manifests.py  the manifests against the Chaos Mesh 2.8.4 and LitmusChaos 3.31.0 CRDs, leniently and strictly
├── check_apis.sh          22 behaviours in the tools', Spring's and the JDK's sources
└── tools/                 Lab.java (services, Toxiproxy process, users), Measure.java
```

## Run

```bash
mvn -B -pl chapters/ch45-chaos-engineering -am verify    # from code/: compile and test
chapters/ch45-chaos-engineering/verify/run.sh            # needs git, curl, a JDK, python3 with PyYAML and jsonschema
```

On Kubernetes with Chaos Mesh installed: `kubectl apply -f src/main/resources/chaos-mesh/menu-gameday-workflow.yaml`
(after `slo-probe` is deployed: a service that answers 200 while the last second met the menu SLO).

## What the experiments found

Two runs on JDK 25.0.4 with Toxiproxy 2.12.0.

| Experiment | Finding |
|---|---|
| Fault matrix | On a 1 KB/s trickle, the JDK client (with or without its request timeout) and `HttpURLConnection` (read timeout 1 s) all hung; only Spring's `JdkClientHttpRequestFactory` gave up at 1 s. The default JDK client hung on a black hole, and retried a dropped connection once (6 s for a 3 s fault). Resets, refusals and truncations failed fast everywhere. |
| Blast radius and guard | +3 s on one of two instances: about 1,500 users hurt in 30 s without a guard, 150–180 with a guard that stopped after 3 bad seconds; on both instances, 93–94% of users while it ran. With a 150 ms read timeout and a retry on the other instance, 0 hurt. |
| Breaker hypothesis | A black hole and no read timeout: the breaker never opened during the fault, 19 of 20 windows failed, about 2,000 calls stuck. With a 250 ms read timeout: the SLO held throughout. A response-side black hole nearly passed the weak configuration. |
| Manifests | All five valid against the released CRDs, strictly; a `durations` typo passes the schema alone and fails strict validation. |
