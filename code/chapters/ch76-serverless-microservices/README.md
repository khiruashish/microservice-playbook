# Chapter 76 — Serverless & Function-Based Services

Code and measurements for [chapter 76](../../../docs/governance-org/ch76-serverless-microservices.md).

| Path | What |
|---|---|
| `src/main/java/.../functions/` | `FeeQuote` (light), `Thumbnail` (CPU-heavy), `FunctionHost` (one `Function<String, String>` behind HTTP, one JVM per instance) |
| `src/main/java/.../platform/` | `Prices` (AWS us-east-1 list prices, October 2026), `Simulation` (a day on functions, scale-to-zero containers, always-on containers) |
| `src/test/java/` | `UnitTest` (3, tag `unit`) |
| `verify/` | `build-libs.sh`, `run.sh`, `check_apis.sh` (7 checks), `tools/` (`Lab`, `RunSuites`) |

```bash
JAVA_HOME=/path/to/jdk-25 verify/run.sh   # about 2 minutes
```

## Findings (JDK 25.0.4, two runs)

**starts** (to the first answer of the function, p50): fee quote JVM 246–299 ms, C1 + Serial 186–193, AOT cache 143–151, AOT + C1 + Serial 113–122 ms (RSS 57 → 43 MB); thumbnail 314–329, 269–285, 207–239, 169–196 ms (70 → 56 MB). Warm 0.57–1.63 ms (fee), 20–26 ms (thumbnail). Caches 16–20 MB, trained in 2.6–3.0 s; writing one needs the application on JARs.

**day** ($ per day; simulated with the measured times): webhooks (2,000) functions $0.0005–0.0006 (0.15% cold at a 5-minute keep-alive, 25% at 1 minute), scale-to-zero $1.05 (12% cold), always-on $2.37; photo bursts functions $0.009–0.010, scale-to-zero $0.049; checkout quotes (1.49 million) functions $0.35–0.42, scale-to-zero $1.19; thumbnails at 12/s functions $0.94–1.02, scale-to-zero $1.19.

**breakeven**: a function call cost 8.1× (fee) and 1.9× (thumbnail) a call on a 70%-busy container; two always-on containers cost less above 106.5 and 28–31 requests a second.

**tests**: unit 3/3. **apis**: 7 of 7 found in Spring Cloud Function 5.0.4 and Knative Serving 1.23.0.
