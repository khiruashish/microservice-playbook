# Chapter 65 — Performance, Chaos & Architecture Tests

Code and measurements for [chapter 65](../../../docs/testing/ch65-performance-chaos-arch-tests.md).

| Path | What |
|---|---|
| `src/main/java/.../load/` | `LoadModels` (closed: looping virtual users; open: arrivals at a rate, latency from the due time), `Latencies` (percentiles, HdrHistogram-style expected-interval correction), `StallingServer` (4 workers, 10 ms per request, optional 1 s freeze every 10 s) |
| `src/main/java/.../chaos/` | `FaultProxy` (latency on a fraction of calls, 503s, a hung dependency), `Services` (menu-service; order-service with no timeout, timeout + retry, timeout + last known price), `Experiment` (hypothesis per window, fault, abort condition) |
| `src/main/java/.../arch/` | `ClassGraph` (dependencies from bytecode with the JDK's ClassFile API, as ArchUnit's importer sees them, or from the whole constant pool as `jdeps` does), `Rules`, `FoodFleetRules`, `Freeze` |
| `src/main/java/.../sample/` | the 10-class service the fitness functions guard |
| `src/test/java/` | `ArchitectureTest` (tag `arch`: the rules in the build), `UnitTest` (4, tag `unit`) |
| `verify/` | `build-libs.sh`, `run.sh`, `check_apis.sh` (14 checks), `violations.tsv` (10 planted violations), `tools/` (`Lab`, `RunSuites`) |

```bash
JAVA_HOME=/path/to/jdk-25 verify/run.sh   # about 10 minutes
```

## Findings (JDK 25.0.4, one 2-vCPU VM, two runs)

**stall**: a service frozen 1 s every 10 s, 60 s of load.

| Model | p90 | p99 | max |
|---|---|---|---|
| closed, 2 virtual users | 11.9–12.1 ms | 15.3–15.7 ms | 1,012–1,020 ms |
| closed, corrected | 14.4–14.9 ms | 898–901 ms | 1,012–1,020 ms |
| open, 150 requests/s | 392–393 ms | 942–943 ms | 1,008–1,012 ms |

**capacity** (about 390 requests/s): closed models from 4 to 32 users plateaued at 362–389 requests/s with p99 rising from 14 to 86 ms (Little's law). Open models: p99 12–14 ms up to 320/s, 18–46 ms at 380/s, and at 420/s only 388–391/s served with p50 747–842 ms and p99 1,495–1,654 ms.

**chaos**: 50 orders/s; 30 s of fault; abort below 90% success per 2 s window.

| Fault | Client | Aborted | OK during fault | p99 |
|---|---|---|---|---|
| 2 s latency on half | no timeout | no | 100% | 2,011–2,013 ms |
| 2 s latency on half | timeout + retry | after 7 s | 81% | 602–603 ms |
| 2 s latency on half | timeout + last price | no | 100% | 302–303 ms |
| hung | no timeout | after 7 s | 13% | 5,001–5,002 ms |
| hung | timeout + retry | after 7 s | 16% | 604–612 ms |
| hung | timeout + last price | no | 100% | 304–307 ms |

**arch**: 10 planted violations, 5 rules: 8 caught. Missed: V8 (an inlined compile-time constant; `jdeps` reports it from the constant-pool entry javac keeps, and reading the whole pool the rules find 4 violations) and V10 (`Class.forName`; nothing sees it). Freezing a legacy version with 5 violations and adding V2: 8 violations, 3 reported. Cost: 2.0 ms to read the sample, 0.2–0.3 ms for all rules; 1.67–1.69 s to read `java.base` (3,655 classes), 33–41 ms for its cycles (stopped at 100).

**tests**: unit 4/4, arch 1/1. **apis**: 14 of 14 found in k6 2.3.0, Gatling 3.16.0, HdrHistogram 2.2.2, Toxiproxy 2.13.0 and ArchUnit 1.5.1.

## Notes

- k6, Gatling, Toxiproxy and ArchUnit weren't run (Maven Central and Go weren't available); `LoadModels`, `FaultProxy` and `ClassGraph`/`Rules` reproduce the behaviours the experiments depend on, and `check_apis.sh` checks those behaviours in the tools' sources. The handbook's parent POM now manages ArchUnit 1.5.1.
- The first version of `ClassGraph` read every class named in the constant pool and so caught V8. ArchUnit's importer is an ASM visitor over members and instructions (`JavaClassProcessor`), which never sees an unused constant-pool entry; `ClassGraph` now reads the same way by default, with the constant-pool mode kept for comparison.
- `java.net.http` shows 22 classes because only `java.*` classes are read (its implementation is in `jdk.internal.net.http`).
- Load generator and service share one VM; at these rates (≤ 420 requests/s) the generator kept up (the open model's achieved rate equals the target until the service's capacity).
