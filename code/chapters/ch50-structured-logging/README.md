# Chapter 50 — Structured Logging & Correlation

Code and measurements for [chapter 50](../../../docs/observability/ch50-structured-logging.md).

| Path | What |
|---|---|
| `src/main/java/.../masking/` | `Masker` (single-pass scan: e-mail and UPI addresses, Indian mobile numbers, Luhn-valid card numbers with a card prefix, JWTs, bearer tokens), `Pii` (values that print masked), `MaskingEncoder` (wraps any Logback encoder) |
| `src/main/java/.../mdc/` | `RequestMdc` (try-with-resources MDC fields), `MdcPropagation` and `MdcTaskDecorator` (MDC across thread hand-offs, worker's own map restored) |
| `src/main/resources/` | `application.yml` (Spring Boot 4.1.1 ECS structured logging), `logback-spring.xml` (ECS behind `MaskingEncoder` and an `AsyncAppender` that doesn't discard) |
| `src/test/java/` | 15 tests |
| `verify/` | `build-libs.sh` (SLF4J 2.0.18 and Logback 1.5.38 from source, OpenTelemetry Java agent 2.32.0, source clones for checks), `run.sh`, `check_apis.sh` (27 checks), `tools/` |

```bash
verify/run.sh                      # all four experiments and the API checks, about a minute
verify/run.sh mdc masking          # selected experiments
```

## Findings (JDK 25.0.4, Logback 1.5.38, agent 2.32.0, 2 vCPUs, two runs)

**mdc** — 200 requests, `request.id` in the MDC, one line from each place (identical in both runs):

- `request.id` right only on the request thread and with `MdcPropagation.wrap` (200/200); missing on 200/200 from a fixed pool, a virtual-thread executor, `CompletableFuture.runAsync`, `new Thread`, `Thread.ofVirtual`.
- A pooled thread after a task that left the MDC set: 100 right, 1 missing, **99 wrong**.
- With the agent, `trace_id` right on every executor hop and on the leaky thread (200/200), missing on bare threads (200/200).

**async** — 8 threads × 2,000 events (10% WARN) into a 50 µs/event sink:

| Appender | INFO delivered | WARN delivered | p99 call | slowest call | burst |
|---|---|---|---|---|---|
| synchronous | 100% | 100% | 14.2–15.0 ms | 37–65 ms | 970–984 ms |
| AsyncAppender defaults | 28.7–29.5% | 100% | 22–26 µs | 75–96 ms | 300–345 ms |
| discardingThreshold 0 | 100% | 100% | 11–13 µs | 108–111 ms | 814–825 ms |
| neverBlock | 2.6% | 8.9–9.0% | 1–6 µs | 1.7–8.4 ms | 10–20 ms |
| queue 8192 + neverBlock | 47.9–54.6% | 100% | 3–9 µs | 2.7–12.6 ms | 9–24 ms |

**cost** — 4 threads × 100,000 events: text 121 B, 664–814 ns buffered; JsonEncoder defaults 365 B, 1,794–1,944 ns; trimmed 289 B, 1,456–1,492 ns; trimmed + masking 290 B, 2,139–2,201 ns (flush-each: 1,062–1,276 / 2,334–2,348 / 2,082–2,175 / 3,068–3,249 ns).

**masking** — five secrets in each of six places:

- No masking leaks 5/5 everywhere.
- `Pii` at the source leaks 5/5 from concatenated message text, MDC values and exception messages.
- `MaskingEncoder` leaks 0 everywhere.
- Cost per event: none 4.4–5.4 µs, `Pii` 3.3–5.1, encoder 17.1–17.9, both 10.6–14.3 (with a stack trace 10.8–12.6 / 6.4–8.8 / 28.1–31.1 / 22.4–26.5).
- Single-pass scanner 3.8–12.6 µs per 519-character line against 69.1–77.2 µs for six regexes. The regexes differed on 44–65 of 600 lines, all of them epoch-millisecond timestamps masked as card numbers.

**apis** — 27 of 27 defaults and behaviours found in Logback 1.5.38, the instrumentation 2.32.0 and Spring Boot 4.1.1.

## Notes

- Spring Boot's structured-logging formatters couldn't be compiled outside a Boot build (they need its `Binder` and Kotlin-backed classes), so the experiments use Logback's `JsonEncoder`. Boot's ECS fields and properties are checked in source by `check_apis.sh`.
- `MdcTaskDecorator` compiles against Spring's `TaskDecorator` from a sparse source clone; `run.sh` doesn't load it.
- `verify/tools/RegexMasker.java` is the first, regex-based masker, kept for the comparison.
