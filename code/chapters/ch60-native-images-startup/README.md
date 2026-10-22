# Chapter 60 — Native Images, CRaC & Startup Optimization

Code and measurements for [chapter 60](../../../docs/deployment/ch60-native-images-startup.md).

| Path | What |
|---|---|
| `src/main/java/.../menu/` | `MenuCatalog`: 150 items priced, grouped, sorted and rendered as JSON per request |
| `src/main/java/.../startup/` | `Warmup`: the request path before readiness, within a time budget |
| `src/main/resources/docker/` | `Dockerfile.jvm`, `.cds` (AppCDS archive from a training run), `.aot` (JDK 25 AOT cache, trained through HTTP), `.aot-core` (the same, trained without HTTP), `.native` (GraalVM executable on glibc only) |
| `src/test/java/` | 5 tests |
| `verify/` | `build-libs.sh` (runtimes, the five images, source clones), `run.sh`, `check_apis.sh` (16 checks), `tools/` (`StartupLab` the service, `Trainer` the training run, `Measure`) |

```bash
JAVA_HOME=/path/to/jdk-25 GRAALVM_HOME=/path/to/graalvm-ce-25 verify/run.sh   # ~6 min to build, ~35 min to measure
SKIP_BUILD=1 verify/run.sh 1                                                   # 1 CPU only, images already built
```

## Findings (JDK 25.0.4, GraalVM CE 25.0.2, Docker 29.4, one 2-vCPU VM, two runs)

Time from `docker run` to the first 200, median of 10 starts, 512 MB:

| Image | Size | 1 CPU | 0.25 CPU | Idle memory (1 CPU) | Requests/s after 1 min (1 CPU / 0.25 CPU) |
|---|---|---|---|---|---|
| JVM | 125 MB | 1,139–1,246 ms | 4,275–4,394 ms | 40–42 MiB | 1,466–1,564 / 392–428 |
| JVM, warmed up (3,000 renders) | 125 MB | 4,276–4,302 ms | 17,534–17,569 ms | 60–65 MiB | 1,680–1,701 / 381–391 |
| JVM + AppCDS | 141 MB | 753–761 ms | 2,488–2,553 ms | 42–43 MiB | 1,449–1,502 / 408–424 |
| JVM + AOT cache | 165 MB | 611–732 ms | 2,043–2,091 ms | 42 MiB | 1,421–1,600 / 318–334 |
| JVM + AOT cache, trained without HTTP | 149 MB | 907–942 ms | 3,066–3,121 ms | 36–37 MiB | 1,411–1,562 / 279–358 |
| Native image | 50.5 MB | 246–266 ms | 280–289 ms | 7.5–7.8 MiB | 994–1,026 / 222–242 |

- First request on a fresh container at 1 CPU: 27–48 ms on the JVM, 16–18 ms with the AOT cache, 2.7–3.8 ms native.
- Build: AOT cache image 14 s; native executable 205 s with 1.73 GB peak memory (33.6 MB executable).
- CRaC: not measured. CRIU failed in the VM (`vdso: Unexpected rt vDSO area bounds ... Could not initialize kernel features detection`); its flags, `Resource` hooks and Spring's `spring.context.checkpoint` are checked in the sources.

**apis**: 16 of 16 found in JDK 25.0.4, GraalVM 25.0.2, CRaC 24+20 and Spring Framework 7.0.9.

## Notes

- Three trial runs came before these two, and changed the experiment:
  - The first trained the cache and the archive with a loop that never started the HTTP server. Its AOT cache saved less than chapter 53's, which led to training through HTTP (`Trainer`) and keeping the old training as the `aot-core` variant.
  - Training through HTTP was first 20 times slower than expected: the JDK's HTTP server sends headers and body in separate writes, and without `sun.net.httpserver.nodelay=true` each response waited about 40 ms for a delayed ACK. `StartupLab` now sets it.
  - The second put the training client in `StartupLab` itself, which made the native executable include the HTTP client (62.7 MB image); `Trainer` is now its own main class.
  - The third was cut short by a VM restart.
- `Measure` counts a dropped keep-alive connection during the 1,000 sequential requests instead of stopping; none were dropped in these runs.
- The first start of each image after the previous image's load test was sometimes slow (up to 6.8 s); medians of 10 aren't affected.
- Spring Boot itself wasn't available here, so the service is plain Java with the libraries Boot services start with (Logback, Micrometer, the Prometheus client). Boot adds context startup, which the AOT cache and Spring AOT shorten further.
