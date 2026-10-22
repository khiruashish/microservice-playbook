# Chapter 66 — Latency, Tail Latency, Virtual Threads vs Reactive

Code and measurements for [chapter 66](../../../docs/performance-scalability/ch66-latency-virtual-threads-reactive.md).

| Path | What |
|---|---|
| `src/main/java/.../quote/` | `QuoteServer`: order-service's quote (three sequential downstream calls) in four models: `platform` (200 threads, Tomcat's default), `virtual` (a virtual thread per request), `async` (a `CompletableFuture` chain on two event-loop threads) and `virtual-limited` (virtual threads, at most 300 in progress, 503 beyond) |
| `src/main/java/.../backend/` | `Backend`: a downstream service with a fast path and a slow share |
| `src/main/java/.../tail/` | `FanOut`: n calls in parallel, optional hedging after a delay, optional hedging budget |
| `src/main/java/.../pinning/` | `Pinning`: 1,000 virtual threads blocking 50 ms inside `synchronized`, a `ReentrantLock` or nothing; compiled for Java 21 |
| `src/test/java/` | `UnitTest` (6, tag `unit`) |
| `verify/` | `build-libs.sh`, `run.sh`, `check_apis.sh` (12 checks), `tools/` (`Lab`, `RunSuites`) |

```bash
JAVA_HOME=/path/to/jdk-25 JDK21_HOME=/path/to/jdk-21 verify/run.sh   # about 10 minutes
```

## Findings (JDK 25.0.4 and 21.0.10, one 2-vCPU VM, two runs)

**models**: backend 50 ms per call; server JVM `-Xmx512m`; open load, 20 s per rate, 10 s timeout; server, backend and load generator on the same 2 CPUs.

| Model | Offered/s | Served/s | p50 | p99 | Rejected | Failed | OS threads | Peak RSS |
|---|---|---|---|---|---|---|---|---|
| platform | 250 | 248 | 155 ms | 187–196 ms | 0 | 0 | 225 | 219–222 MB |
| platform | 1,000 | 992 | 156–160 ms | 188–246 ms | 0 | 0 | 225 | 232–233 MB |
| platform | 1,500 | 1,060–1,100 | 3,657–4,390 ms | 7,219–8,260 ms | 0 | 0 | 225 | 658–659 MB |
| platform | 2,500 | 405–420 | 6.9–13.1 s | 16.7–19.3 s | 0 | 36,623–37,998 | 225 | 658–660 MB |
| virtual | 250 | 248 | 153–154 ms | 179–191 ms | 0 | 0 | 25 | 187–194 MB |
| virtual | 1,000 | 992 | 155–162 ms | 198–249 ms | 0 | 0 | 25 | 205–268 MB |
| virtual | 1,500 | 1,488–1,489 | 174–181 ms | 354–411 ms | 0 | 0 | 25 | 330–332 MB |
| virtual | 2,500 | 190–577 | 1,035–1,137 ms | 4,647–6,514 ms | 0 | 45,446–46,544 | 25–26 | 640 MB |
| async | 250 | 248 | 153–154 ms | 180–190 ms | 0 | 0 | 25 | 189–192 MB |
| async | 1,000 | 992 | 159–160 ms | 237–247 ms | 0 | 0 | 25 | 250–260 MB |
| async | 1,500 | 1,487–1,488 | 180–181 ms | 279–404 ms | 0 | 0 | 25 | 319–335 MB |
| async | 2,500 | 538–839 | 1,358–2,090 ms | 5,616–6,326 ms | 0 | 43,100–45,526 | 25 | 639 MB, OutOfMemoryError |
| virtual-limited | 250 | 248 | 153–154 ms | 176–178 ms | 0 | 0 | 25 | 186–192 MB |
| virtual-limited | 1,000 | 992 | 156–157 ms | 206–208 ms | 0 | 0 | 25 | 201–203 MB |
| virtual-limited | 1,500 | 1,383–1,397 | 178–182 ms | 280–300 ms | 1,833–2,079 | 0 | 25 | 300–302 MB |
| virtual-limited | 2,500 | 1,510–1,588 | 181–186 ms | 261–303 ms | 17,920–19,570 | 0 | 25 | 302–338 MB |

**tail**: backend 5 ms, 200 ms for 1 in 100; 2,000 requests per row.

| n | In flight | Hedge | p50 | p99 | > 100 ms | Extra calls |
|---|---|---|---|---|---|---|
| 1 | 4 | no | 6.6 ms | 17.6–200.6 ms | 0.8–1.1% | |
| 1 | 4 | 20 ms | 6.6 ms | 26.2–27.0 ms | 0% | 1.6–2.2% |
| 5 | 20 | no | 6.7–6.9 ms | 203 ms | 5.0–5.1% | |
| 5 | 20 | 20 ms | 6.4–6.7 ms | 27 ms | 0–0.1% | 0.9–1.1% |
| 20 | 80 | no | 7.3–7.6 ms | 203–205 ms | 17.2–19.1% | |
| 20 | 80 | 20 ms | 8.8–9.8 ms | 33.0–36.5 ms | 0.1–0.2% | 1.5–2.0% |
| 50 | 200 | no | 11.4–12.5 ms | 208–210 ms | 39.1–40.5% | |
| 50 | 200 | 20 ms | 18.5–21.4 ms | 52.9–86.1 ms | 0.4–0.9% | 7.6–10.1% |
| 50 | 1,000 | no | 32.1–33.8 ms | 233–239 ms | 40.4–40.9% | |
| 50 | 1,000 | 20 ms | 157–163 ms | 362–407 ms | 92.7–94.5% | 99.9% |
| 50 | 1,000 | 20 ms, budget 5% | 44.6–45.5 ms | 269–280 ms | 31.1–31.5% | 5.3% |

**pinning**: 1,000 virtual threads, 50 ms each inside their own lock, 2 carriers. JDK 21.0.10: none 0.09–0.11 s, `ReentrantLock` 0.12 s, `synchronized` **25.16–25.17 s** (40 tasks/s). JDK 25.0.4: 0.10–0.12 s, 0.11 s, 0.10–0.11 s.

**tests**: unit 6/6. **apis**: 12 of 12 found in JDK 21.0.10, JDK 25.0.4, Spring Boot 4.1.1, Reactor Netty 1.3.7 and Reactor Core 3.8.7.

## Notes

- Spring Boot, Tomcat and Reactor weren't run (Maven Central wasn't reachable). The models reproduce their thread set-ups with the JDK's `HttpServer` and `HttpClient`; `check_apis.sh` checks the defaults in their sources. Reactor Netty would start 4 event-loop threads on 2 CPUs (max(CPUs, 4)); `async` uses 2.
- The JDK's HTTP server keeps at most 200 idle keep-alive connections by default (`sun.net.httpserver.maxIdleConnections`) and closes the rest; clients then failed with "header parser received no bytes" when reusing them. The servers raise it to 4,000 and set `sun.net.httpserver.nodelay=true`.
- At 2,500 quotes a second the machine handles 10,000 HTTP exchanges a second for server, backend and load generator together, more than 2 CPUs can do: that rate tests behaviour past capacity, not a model's own limit. In earlier trial runs the unbounded `virtual` server also died with `OutOfMemoryError` at that rate.
- Latencies are of successful requests only; 503s count as rejected, and errors and timeouts as failed. Peak OS threads and RSS are sampled from `/proc` every 0.5 s.
- The hedged rows at 1,000 calls in flight are deliberately saturated: there the fast path itself exceeds the 20 ms hedge delay.
- At one call per request, p99 sits on the boundary between the fast 99% and the slow 1%, so it jumps between runs (17.6 and 200.6 ms).
