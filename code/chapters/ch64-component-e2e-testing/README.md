# Chapter 64 — Component & End-to-End Testing

Code and measurements for [chapter 64](../../../docs/testing/ch64-component-e2e-testing.md).

| Path | What |
|---|---|
| `src/main/java/.../app/` | `MenuApp` (prices; 5 portions of the biryani of the day), `OrderApp` (reserve, FIRST20 once per customer in PostgreSQL, store, ask for a courier), `DeliveryApp` (202 at once, a courier after a log-normal delay), `Main` (one image, three roles), `Web` |
| `src/main/java/.../e2e/` | `Journeys`: the five E2E journeys, and `Wait` (sleep then check, or poll) |
| `src/main/resources/k8s/environment.yaml` | a whole environment in one namespace: PostgreSQL and the three services |
| `src/test/java/` | `OrderComponentTest` (5, tag `component`: order-service with stubs and PostgreSQL in Docker), `UnitTest` (4, tag `unit`) |
| `verify/` | `build-libs.sh` (the image, loaded into the cluster; libraries; source clones), `run.sh`, `check_apis.sh` (7 checks), `tools/` (`Lab`, `RunSuites`) |

```bash
../../ch52-health-checks-actuator/verify/k3s-lab.sh   # a cluster
JAVA_HOME=/path/to/jdk-25 verify/run.sh               # about 15 minutes
```

## Findings (k3s v1.37.1+k3s1, one 2-vCPU node, JDK 25.0.4, two runs)

**environments**: PostgreSQL 16 and three Java services, one namespace each.

| At once | Answering | Deleted |
|---|---|---|
| 1 | 6.8–7.5 s | 10.7–10.8 s |
| 4 | 14.1–19.5 s | 10.7–13.5 s |

**staging**: four pipelines, three suite runs each (12 runs, 60 journeys), polling.

| Environment | Suites passed | Journeys failed |
|---|---|---|
| shared, fixed test data | 1 of 12 | 18 (FIRST20 used 11, sold out 7) |
| shared, unique test data | 5 of 12 | 7 (sold out) |
| fresh per run, fixed data | 12 of 12 | 0 |

**waits**: the courier journey, 200 times per strategy; assignment delay log-normal, median 300 ms.

| Strategy | Failed | Mean time |
|---|---|---|
| sleep 0.5 s | 54–56 (27–28%) | 582–608 ms |
| sleep 1 s | 19–25 (9.5–12.5%) | 1,044–1,046 ms |
| sleep 2 s | 2–7 (1–3.5%) | 2,038 ms |
| poll 100 ms, up to 10 s | 0 | 580–678 ms (max 9,980 ms) |

**component**: the five journeys, three times each: component tests 1.9–2.3 s in total; E2E in a fresh environment 19.0–28.1 s (creating and deleting it included). All passed.

**tests**: unit 4/4, component 5/5. **apis**: 7 of 7 found in Awaitility 4.3.0, Argo CD 3.5.5 and Spring Boot 4.1.1.

## Notes

- The first full run stopped when an environment's order-service never seemed to answer. Deleted environments' cluster IPs are reused by new ones, and the test runner's HTTP client kept pooled keep-alive connections to an old IP whose pod was gone. `Lab` now drops idle connections after a second (`jdk.httpclient.keepalive.timeout=1`). Long-lived test runners against short-lived environments need the same care.
- The very first environment after loading the images took 16 s; the tables are with images on the node.
- The test runner ran on the node and called cluster IPs directly; in CI it would run in the cluster or through an ingress.
- The services are plain Java (no Spring Boot, which wasn't available here); the patterns map to `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ServiceConnection` and WireMock or Spring Cloud Contract stubs.
