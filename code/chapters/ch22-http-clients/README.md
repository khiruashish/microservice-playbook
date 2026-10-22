# Chapter 22 — HTTP Clients

tracking-service calls order-history-service (chapter 21). Two parts:

| Package | What it is |
|---|---|
| `experiment/` | `LocalServer` (HTTP or HTTPS with a keytool-made certificate; `/fast`, `/slow` 200 ms, `/hang` 60 s) and `ClientExperiments`: client reuse over TLS, timeouts, concurrency styles |
| `client/` | `OrderHistoryApi` (`@HttpExchange`), group configuration with a `RestClientHttpServiceGroupConfigurer`, `ProblemDetailErrorHandler` (RFC 9457 → `ApiProblemException`), `OrderHistoryReader` (lazy cursor paging, ETag revalidation) |

## Run

```bash
cd code
mvn -B -pl chapters/ch22-http-clients -am verify
java -cp chapters/ch22-http-clients/target/classes dev.foodfleet.ch22.experiment.ClientExperiments
```

## Verified output

2-vCPU Linux container, JDK 21 runtime (the module targets JDK 25), loopback. Absolute numbers will differ on your machine; across three runs
the shared client's p50 was 2.1–2.6 ms vs 13.7–16.2 ms for a new client per call, the 50-thread pool took
4.16–4.18 s, virtual threads 0.52–0.70 s, the async API 1.1–1.4 s.

```text
1. Reuse over HTTPS: 300 sequential GETs to a local server
                                        p50 ms   p99 ms   total ms
   one shared client (keep-alive)         2.14     8.18        735
   new client per call (new TLS)         15.75    33.64       5090
2. Timeouts: GET /hang (the server takes 60 s)
   no request timeout:    still blocked after 3005 ms (a request thread, a connection and a caller held)
   300 ms request timeout: HttpTimeoutException after 302 ms
3. Concurrency: 1,000 calls to an endpoint that takes 200 ms
   PLATFORM_POOL_50                       4173 ms
   VIRTUAL_THREADS                         560 ms
   ASYNC_API                              1374 ms
   server's peak concurrent requests: 1000
```
