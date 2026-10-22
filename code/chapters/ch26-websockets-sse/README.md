# Chapter 26 — WebSockets & Server-Sent Events

Live order tracking: riders stream positions in over a WebSocket, customers receive them over SSE.

| Package | What it is |
|---|---|
| `experiment/` | `TransportComparison`: short polling, long polling, SSE and WebSocket over real loopback sockets through `ByteCountingProxy`; `TrackingHttpServer` (JDK HttpServer) and `MiniWebSocketServer` (just enough RFC 6455, checked by the JDK WebSocket client). `ConnectionCapacity` + `CapacityServer`: N open SSE streams on a 200-thread pool vs virtual threads, server in its own JVM. `ReconnectStorm`: 200,000 clients reconnecting under five policies |
| `tracking/` | Spring: `TrackingStreamController` (`SseEmitter`, `Last-Event-ID` replay, randomised `retry:`, heartbeats, 503 when full), `RiderLocationHandler` + `WebSocketConfig` + `RiderIdentityInterceptor` (auth at the handshake, validation, 1/s rate limit, `ConcurrentWebSocketSessionDecorator`), `TrackingHub` |

## Run

```bash
cd code
mvn -B -pl chapters/ch26-websockets-sse -am verify
CP=chapters/ch26-websockets-sse/target/classes
java -cp $CP dev.foodfleet.ch26.experiment.TransportComparison      # ~1 min
java -cp $CP dev.foodfleet.ch26.experiment.ConnectionCapacity 10000 # needs ulimit -n >= 20000
java -cp $CP dev.foodfleet.ch26.experiment.ReconnectStorm
```

## Verified output

2-vCPU Linux container, JDK 21 runtime (the module targets JDK 25), loopback. Three runs of
`TransportComparison` gave identical byte counts except short polling's (2,202–2,345 bytes down, 10–11
updates seen); its mean staleness was 526–609 ms.

```text
one tracking screen: 40 position updates, one every 250 ms; 700-char bearer token per request

transport             requests  conns  bytes up bytes down  updates seen bytes/update stale mean stale p95
SHORT_POLLING_1S            12      1      9704       2202         10/40         1191      564.3     999.7
LONG_POLLING                40      1     33364       8504         40/40         1047        2.9       6.5
SERVER_SENT_EVENTS           1      1       826       3963         40/40          120        1.3       2.7
WEBSOCKET                    1      1       923       3114         40/40          101        1.0       2.3
```

Clients and server share the 2 vCPUs, so broadcast latencies are pessimistic. Two runs:

```text
10000 SSE clients, one broadcast event; server in its own JVM (-Xmx1g)

executor    clients  streams open  received   p50 ms   p99 ms      RSS +MB  KB/stream
pool200       10000           200       200     19.7     26.2         63.4      324.6
virtual       10000         10000     10000    229.9    387.4        464.8       47.6
pool200       10000           200       200     89.9    100.3         53.5      273.8
virtual       10000         10000     10000    220.6    316.8        491.7       50.4
```

```text
200,000 clients reconnect at once; fleet capacity 20,000 handshakes/s; client timeout 5 s

reconnect policy                 99% back after    peak attempts/s    attempts/client wasted handshakes
IMMEDIATE_THEN_1S                         20.4s             200000               2.05           209000
FIXED_3S                                  20.9s             200000               1.68           135400
EXPONENTIAL_NO_JITTER                     19.9s             200000               1.78           155600
EXPONENTIAL_FULL_JITTER                    9.9s              20233               1.00                0
IMMEDIATE_SERVER_SHEDS_STALE              10.9s             200000               1.49                0
```

## Tests

| Test | Checks |
|---|---|
| `ExperimentsTest` | RFC 6455 accept key and frame bytes; push sees all 40 updates for a fraction of polling's bytes; a 200-thread pool holds 200 streams, virtual threads 500 of 500; jitter and shedding in the storm |
| `TrackingIntegrationTest` | `@SpringBootTest(RANDOM_PORT)` with the JDK clients: rider position over WebSocket reaches the SSE stream; `Last-Event-ID` replay; 401 without the rider header; malformed message closes with 1007 |
