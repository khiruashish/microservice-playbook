# Chapter 69 — High-Throughput Kafka Tuning

Code and measurements for [chapter 69](../../../docs/performance-scalability/ch69-kafka-throughput-tuning.md).

| Path | What |
|---|---|
| `src/main/java/.../settings/` | `KafkaProfiles`: producer (defaults, throughput, latency) and consumer (batch sink) settings as Java client properties, and as Spring Boot properties |
| `src/main/java/.../sink/` | `OrderSink`: a consumer's writes to PostgreSQL per record, per poll with JDBC batching, one multi-row insert, or COPY through a temporary table; all idempotent (`on conflict do nothing`); `Events` |
| `src/test/java/` | `UnitTest` (3, tag `unit`) |
| `verify/kafkalab/` | Go: the producer and consumer experiments with franz-go's client (`kgo`) against franz-go's in-memory Kafka broker (`kfake`) in a separate process, which can delay responses by a round trip |
| `verify/` | `build-libs.sh`, `run.sh`, `check_apis.sh` (16 checks), `tools/` (`Lab`: the sink experiment; `RunSuites`) |

```bash
JAVA_HOME=/path/to/jdk-25 verify/run.sh   # about 10 minutes; needs Go 1.24+ and Docker
```

## Findings (one 2-vCPU VM, JDK 25.0.4, Go 1.24.7, two runs)

**producer, steady 10,000 records/s for 10 s** (300-byte JSON events, 12 partitions, acks=all, idempotent):

| Settings | RTT | p50 | p99 | Requests | Broker CPU |
|---|---|---|---|---|---|
| linger.ms=0, batch.size=16384 | loopback | 0.37–0.44 ms | 2.8–3.2 ms | 66,433–69,432 | 5.75–6.02 s |
| linger.ms=5, batch.size=16384 | loopback | 3.7–3.8 ms | 7.4–7.8 ms | 3,259–3,343 | 0.69–0.75 s |
| linger.ms=20, batch.size=131072 | loopback | 11.5 ms | 22.1–22.2 ms | 920–953 | 0.24–0.27 s |
| linger.ms=0 | 1 ms | 2.3 ms | 4.4–5.4 ms | 19,551–19,918 | 3.08–3.15 s |
| linger.ms=5 | 1 ms | 5.0 ms | 9.0–9.3 ms | 3,216–3,237 | 0.92–0.97 s |
| linger.ms=20, batch.size=131072 | 1 ms | 12.8–12.9 ms | 23.5–23.8 ms | 949–957 | 0.31–0.34 s |

**producer, as fast as possible** (300,000 records): 16 KB batches 629,000–870,000 records/s in about 500 requests; 128 KB batches 849,000–969,000 in 65–75; lz4 577,000–827,000 (4.53×), snappy 800,000–936,000 (4.38–4.39×), zstd 339,000–480,000 (6.75×), gzip 121,000–127,000 (7.31×, 2.5–2.6 s of client CPU); one request in flight without idempotence 584,000–661,000 on loopback and 268,000–269,000 over 1 ms.

**consumer** (6 partitions; 1 ms per record): 1 / 3 / 6 / 12 consumers 852–860 / 2,468–2,518 / 4,888–4,897 / 4,825–4,923 records/s (6 idle at 12); 32 key-ordered lanes in one consumer 16,616–16,774; one 5 ms write per poll 61,931–63,239 (1 consumer), 149,683–152,454 (6).

**sink** (PostgreSQL 16, synchronous commits): per record 1,384–1,554 rows/s; JDBC batch per poll of 50 / 500 / 5,000: 15,554–17,252 / 29,449–33,861 / 34,511–35,727; `reWriteBatchedInserts` 45,496–46,395; multi-row 43,222–43,774; COPY 500 / 5,000: 32,801–33,064 / 55,025–58,548. Every sink had 100,000 rows after a fifth was replayed.

**tests**: unit 3/3. **apis**: 16 of 16 found in Kafka 4.2.2, franz-go 1.20.6 and pgjdbc 42.7.14.

## Notes

- The Kafka Java client and broker couldn't be fetched (no Maven Central or Apache downloads from the test machine), so kafkalab uses franz-go, a production Kafka client, and kfake, its in-memory broker that speaks the Kafka protocol. kfake has no disk and no replication, so broker CPU is kfake's and only comparable between rows; `acks=all` waits for nothing extra. Request counts, batch sizes, compression ratios and consumer parallelism don't depend on that.
- franz-go's defaults differ from the Java client's (10 ms linger, snappy, 1 MB batches); kafkalab sets each explicitly. With idempotence it keeps 5 requests in flight like the Java client, and it can't limit an idempotent producer to 1, so the one-in-flight rows turn idempotence off.
- franz-go and kfake are fetched from GitHub at commit 2437c0f4 (kfake) and v1.20.6 (the client), the last versions that build with Go 1.24; golang.org/x/crypto comes from its GitHub mirror (`go.mod`'s replace).
- The trial run before the two runs gave the same picture; its numbers aren't used.
