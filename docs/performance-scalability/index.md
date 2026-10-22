---
title: "Part 10 — Performance & Scalability"
description: "How FoodFleet makes services fast, keeps them fast under load, and pays only for what it needs: latency budgets and concurrency models, JVM settings in containers, horizontal scaling and partitioning, Kafka throughput, and cost, each measured with real services under open load, Docker containers with limits, a Kubernetes cluster and a Kafka client."
progress: done
---

# Part 10 — Performance & Scalability

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

Part 9 tested qualities. Part 10 engineers them:

- **Latency and concurrency**: where time goes in a request, and which concurrency model to serve it with.
- **The runtime**: what the JVM decides inside a container, and how to correct it.
- **Scale**: what makes more copies mean more capacity, and how to split data and work.
- **Throughput**: batching and parallelism on Kafka, from producer to database.
- **Cost**: what services use against what they ask for, and what that costs.

The recurring lesson is that the defaults decide more than the code does:

- A 200-thread pool.
- A collector chosen from a container's memory.
- An HTTP keep-alive connection that never moves.
- `linger.ms=0`.
- Requests nobody measured.

Every chapter measures its claims, with:

- Open-model load.
- Docker containers with CPU and memory limits.
- A k3s 1.37 cluster.
- PostgreSQL 16.
- A Kafka client.
- The sources of the JDK, Spring Boot, Reactor, Kubernetes, Kafka, the Vertical Pod Autoscaler and OpenCost.

## FoodFleet's performance, layer by layer

| Layer | What decides it | FoodFleet's choice | Chapter |
|---|---|---|---|
| Request | Latency budget, concurrency model, limits | MVC on virtual threads, JDK 25, a concurrency limit per endpoint | [66](ch66-latency-virtual-threads-reactive.md) |
| JVM | Collector, heap share, native memory | G1 by default (ZGC for latency-critical services with 2+ CPUs), heap from the measured live set, JFR always on | [67](ch67-jvm-tuning-containers.md) |
| Service | State, balancing, shared bottlenecks | Stateless copies, per-request balancing, autoscaling on the limiting signal | [68](ch68-horizontal-scaling.md) |
| Data and events | Partition keys, batching, sinks | Keys for ordering and skew; producer batching and lz4; a transaction per poll | [68](ch68-horizontal-scaling.md), [69](ch69-kafka-throughput-tuning.md) |
| Cost | Requests against usage, capacity type | Requests from VPA recommendations, no CPU limits at the p90, spot above an on-demand base, showback by team | [70](ch70-finops.md) |

## Decision guide

| If your problem is… | Reach for | Chapter | What the chapter measured |
|---|---|---|---|
| Latency rises with load but CPU is idle | Virtual threads (or an event loop) | [66](ch66-latency-virtual-threads-reactive.md) | 200 platform threads capped a quote at 1,060–1,100/s; virtual threads reached about 1,490/s with 25 OS threads |
| A service falls over past capacity | A concurrency limit with fast rejection | [66](ch66-latency-virtual-threads-reactive.md) | Unbounded: 86–93% failed; a limit of 300 served 1,510–1,588/s at p99 ≤ 303 ms |
| Requests that fan out are slow | Fewer calls, or hedged reads with a budget | [66](ch66-latency-virtual-threads-reactive.md) | 50 calls with 1% slow: 39–41% of requests slow; hedging cut p99 to 53–86 ms |
| Long GC pauses in small pods | An explicit collector | [67](ch67-jvm-tuning-containers.md) | A 2-CPU, 1 GiB pod got Serial: 655–704 ms pauses |
| OOMKilled pods | Heap from the live set, non-heap measured with NMT | [67](ch67-jvm-tuning-containers.md) | 90% heap + 150 MB of direct buffers was killed in one run of two |
| New pods get no traffic | Per-request balancing, connection recycling | [68](ch68-horizontal-scaling.md) | After scaling 2 → 4 with keep-alive, new copies got 0% |
| More pods, same throughput | Find and scale the shared bottleneck | [68](ch68-horizontal-scaling.md) | 4 copies served 50/s behind a 50/s dependency |
| Resharding moves everything | Consistent, rendezvous or jump hashing | [68](ch68-horizontal-scaling.md) | mod n moved 80% of keys going 4 → 5; the others 20% |
| Brokers busy at modest traffic | Producer batching | [69](ch69-kafka-throughput-tuning.md) | linger 0: 66,000–69,000 requests for 100,000 records; 20 ms: about 950 |
| Consumer lag that won't clear | Batch writes, key-ordered concurrency | [69](ch69-kafka-throughput-tuning.md) | A commit per record: 1,384–1,554 rows/s; a poll per transaction 29,000–59,000 |
| A large, mostly idle cluster | Requests from measured usage | [70](ch70-finops.md) | Workloads used 3–10% of their requested CPU; right-sized, 97 → 30 CPUs and 32 → 8–9 nodes |
| Saving on compute without outages | Spot above an on-demand base | [70](ch70-finops.md) | All-spot served 0–1% during a reclaim; 3 on-demand + 3 spot served 55–61% |

## The chapters

<div class="grid cards" markdown>

-   **[66. Latency, Tail Latency, Virtual Threads vs Reactive](ch66-latency-virtual-threads-reactive.md)**

    ---

    Four concurrency models under open load past capacity; fan-out tails and hedging; pinning on JDK 21 and 25.

-   **[67. JVM Tuning in Containers](ch67-jvm-tuning-containers.md)**

    ---

    Ergonomics in fourteen container sizes; six collectors; heap share; native memory; JFR's cost. *Docker per measurement.*

-   **[68. Horizontal Scaling & Partitioning](ch68-horizontal-scaling.md)**

    ---

    Session state, connection-level balancing, shared bottlenecks; partitioning schemes and hot keys. *A k3s 1.37 cluster.*

-   **[69. High-Throughput Kafka Tuning](ch69-kafka-throughput-tuning.md)**

    ---

    linger.ms, batch.size, compression and in-flight requests; consumers and partitions; batch sinks. *franz-go and PostgreSQL.*

-   **[70. Cost Optimization (FinOps)](ch70-finops.md)**

    ---

    Usage against requests, VPA-style right-sizing, packing and showback, spot reclaims under load. *Docker and k3s.*

</div>

## Reading order by experience

| You are | Read | Then |
|---|---|---|
| **Building services already** (🔵 L2) | 68 → 66 | [Part 11: Governance & Organization](../governance-org/index.md) |
| **Leading a team** (🟣 L3) | 66 → 67 → 68 → 69 | [Part 11: Governance & Organization](../governance-org/index.md) |
| **Owning a platform or a budget** (🔴 L4) | 67 → 68 → 70 | [Part 11: Governance & Organization](../governance-org/index.md) |
| **Preparing for interviews** | 66, 68, 69 (each ends with 12 questions) | [Interview prep](../interview-prep/index.md) |

## How the FoodFleet story builds up

<ul class="mh-timeline">
<li><span class="when">ADR-066 · Chapter 66</span><span class="what">MVC on virtual threads on JDK 25; a concurrency limit per endpoint from Little's law; latency budgets per hop; hedged idempotent reads within a 5% budget.</span></li>
<li><span class="when">ADR-067 · Chapter 67</span><span class="what">Collectors set explicitly; heap from the measured live set with 2.5× headroom; container memory = heap + measured non-heap + 15%; ExitOnOutOfMemoryError; continuous JFR.</span></li>
<li><span class="when">ADR-068 · Chapter 68</span><span class="what">No session state in memory; no affinity; per-request balancing by the mesh; shared bottlenecks named per service; partition keys chosen for ordering and skew.</span></li>
<li><span class="when">ADR-069 · Chapter 69</span><span class="what">Event producers batch (20 ms, 128 KB, lz4); partitions for the slowest consumer; batch listeners writing a poll per transaction; key-ordered concurrency.</span></li>
<li><span class="when">ADR-070 · Chapter 70</span><span class="what">Requests from VPA recommendations; no CPU limits on services; showback by max(request, usage); spot above an on-demand base sized for the peak; cost per 1,000 orders beside SLOs.</span></li>
</ul>

!!! tip "Running the code"
    ```bash
    cd code
    mvn -B -pl chapters/ch66-latency-virtual-threads-reactive,chapters/ch67-jvm-tuning-containers,chapters/ch68-horizontal-scaling,chapters/ch69-kafka-throughput-tuning,chapters/ch70-finops -am verify
    ```
    JDK 25 and Maven 3.9+. Each chapter's `verify/` folder holds its experiments and says what they need:

    - **66:** a JDK 25 and a JDK 21.
    - **67:** Docker.
    - **68 and 70:** Docker and a Kubernetes cluster (chapter 52's `k3s-lab.sh` starts one).
    - **69:** Go 1.24+ and Docker.
