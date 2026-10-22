---
title: "Cheat Sheets"
description: "The commands and settings used most often with FoodFleet's stack: Kafka's CLI for topics, consumer groups and offsets; kubectl for diagnosing and rolling back services; Helm 4 for rendering, installing and rolling back the shared chart; and FoodFleet's measured Resilience4j defaults, included from chapter 43's own configuration."
progress: done
---

# Cheat Sheets

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

Commands are shown for the tool versions the handbook used (Kafka 4.x, Kubernetes 1.37, Helm 4.1). Flags change between major versions; `--help` is the authority.

## Kafka

Run from the broker's `bin/` directory (or the `apache/kafka` image's `/opt/kafka/bin/`). `B=--bootstrap-server localhost:9092`.

### Topics

```bash
# create a keyed event topic: partitions for the slowest consumer's parallelism plus growth (ADR-069)
kafka-topics.sh $B --create --topic foodfleet.ordering.order-events --partitions 24 --replication-factor 3 \
  --config min.insync.replicas=2

# a compacted state topic (ADR-019): the newest record per key is kept
kafka-topics.sh $B --create --topic foodfleet.catalog.menu-items --partitions 12 --replication-factor 3 \
  --config cleanup.policy=compact --config min.insync.replicas=2

kafka-topics.sh $B --list
kafka-topics.sh $B --describe --topic foodfleet.ordering.order-events     # leaders, replicas, in-sync replicas
kafka-topics.sh $B --describe --under-replicated-partitions               # anything not fully replicated

# change a topic's configuration
kafka-configs.sh $B --entity-type topics --entity-name foodfleet.ordering.order-events \
  --alter --add-config retention.ms=604800000
kafka-configs.sh $B --entity-type topics --entity-name foodfleet.ordering.order-events --describe
```

!!! warning "Adding partitions to a keyed topic"
    `kafka-topics.sh --alter --partitions` moves keys to new partitions and breaks their order. Plan it as a migration ([Kafka topic design](../food-delivery-platform/kafka-design.md#keys-and-partitions)).

### Consumer groups and lag

```bash
kafka-consumer-groups.sh $B --list
kafka-consumer-groups.sh $B --describe --group kitchen-service    # CURRENT-OFFSET, LOG-END-OFFSET, LAG per partition
kafka-consumer-groups.sh $B --describe --group kitchen-service --members --verbose   # who has which partitions

# reset offsets: the group must have no active members. Always --dry-run first, then --execute
kafka-consumer-groups.sh $B --group reporting-service --topic foodfleet.ordering.order-events \
  --reset-offsets --to-datetime 2026-10-21T00:00:00.000 --dry-run
kafka-consumer-groups.sh $B --group reporting-service --topic foodfleet.ordering.order-events \
  --reset-offsets --to-earliest --execute
```

### Reading and writing

```bash
# read with keys, partitions and headers: the fastest way to see what a producer really sends
kafka-console-consumer.sh $B --topic foodfleet.ordering.order-events --from-beginning --max-messages 10 \
  --property print.key=true --property print.partition=true --property print.headers=true

# a dead-letter topic, to see the error headers Spring Kafka adds
kafka-console-consumer.sh $B --topic foodfleet.ordering.order-events-dlt --from-beginning --property print.headers=true

# write keyed records (key:value per line)
kafka-console-producer.sh $B --topic test --property parse.key=true --property key.separator=:
```

### Producer and consumer settings FoodFleet uses

| Setting | Value | Why | Decision |
|---|---|---|---|
| `acks` | `all` | No acknowledged record lost to a broker failure | [ADR-069](../performance-scalability/ch69-kafka-throughput-tuning.md) |
| `enable.idempotence` | `true` | No duplicates from producer retries | ADR-069 |
| `linger.ms` / `batch.size` | `20` / `131072` (events) | ~950 requests a second instead of ~67,000 at 10,000 records a second | ADR-069 |
| `compression.type` | `lz4` | 4.4–4.5× on order events, at full speed | ADR-069 |
| `min.insync.replicas` (topic) | `2` | With `acks=all`, a record is on two brokers before it's acknowledged | [ch. 69](../performance-scalability/ch69-kafka-throughput-tuning.md) |
| Commit | After processing, by the listener container | At-least-once, made safe by an inbox | [ADR-025](../communication/ch25-asynchronous-messaging.md) |

## kubectl

```bash
# what's running, and why not
kubectl get pods -n orders -o wide
kubectl describe pod <pod> -n orders            # events at the bottom: scheduling, probes, OOMKilled
kubectl get events -n orders --sort-by=.lastTimestamp
kubectl logs <pod> -n orders -c app --previous  # the container that crashed, not its replacement
kubectl top pod -n orders --containers          # needs metrics-server

# rollouts
kubectl rollout status deployment/order-service -n orders
kubectl rollout history deployment/order-service -n orders
kubectl rollout undo deployment/order-service -n orders    # outside GitOps only: with Argo CD, revert the commit

# debugging a distroless container: no shell inside, so attach an ephemeral one
kubectl debug -it <pod> -n orders --image=busybox:1.37 --target=app
kubectl port-forward deployment/order-service -n orders 8081:8081     # the Actuator port (ADR-052)

# what would change, checked by the API server without changing anything
kubectl apply --dry-run=server -f rendered.yaml -n orders
kubectl diff -f rendered.yaml -n orders

# who may do what
kubectl auth can-i create deployments -n orders --as=system:serviceaccount:orders:order-service

# autoscaling and disruption
kubectl get hpa -n orders
kubectl describe hpa order-service -n orders    # current vs target, and recent scaling events
kubectl get pdb -n orders
```

Reading pod states:

| You see | It usually means | Look at |
|---|---|---|
| `Pending` | No node fits the requests, or a PVC isn't bound | `describe pod` events |
| `CrashLoopBackOff` | The process exits; Kubernetes backs off restarts | `logs --previous` |
| `OOMKilled` (in `describe`) | The container exceeded its memory limit | Heap vs non-heap ([ch. 67](../performance-scalability/ch67-jvm-tuning-containers.md)) |
| Running, 0/1 ready | Readiness failing | The readiness group's contents ([ch. 52](../observability/ch52-health-checks-actuator.md)) |
| `ImagePullBackOff` | Wrong digest or registry credentials; or admission refused a signature | `describe pod`; Kyverno's policy reports |

## Helm 4

```bash
helm lint ./foodfleet-service -f values/order-service.yaml
helm template order-service ./foodfleet-service -f values/order-service.yaml -n orders > rendered.yaml

# install or upgrade; Helm 4 applies server-side, and rolls back if the release doesn't become ready
helm upgrade --install order-service ./foodfleet-service -f values/order-service.yaml -n orders \
  --rollback-on-failure --timeout 5m

helm list -n orders
helm history order-service -n orders
helm rollback order-service 7 -n orders
helm get values order-service -n orders          # what this release was installed with
helm get manifest order-service -n orders        # what it rendered
```

`--rollback-on-failure` is Helm 4's name for Helm 3's `--atomic`. Under Argo CD the chart is rendered and applied by Argo CD, not by `helm upgrade`, so these release commands apply to clusters deployed with Helm directly ([CI/CD & GitOps](../food-delivery-platform/cicd.md#where-the-delivery-decisions-dont-fit-together-yet)).

## Resilience4j: FoodFleet's defaults

Chapter 43 measured the library's defaults (breakers that opened in 0.5–10 s depending on traffic, 60 s open states, a slow-call threshold of 60 s) and set these instead ([ADR-043](../resilience/ch43-resilience4j-deep-dive.md)):

```yaml title="code/chapters/ch43-resilience4j/src/main/resources/order-service.yml"
--8<-- "code/chapters/ch43-resilience4j/src/main/resources/order-service.yml"
```

| Setting | Library default | FoodFleet | Why |
|---|---|---|---|
| Window | 100 calls, count-based | 5 seconds, time-based | A count window took ~10 s to open on a quiet endpoint; 5 s opened in 2–4 s at 100/s and at 5/s |
| Minimum calls | 100 | 20 | So quiet dependencies are protected at all |
| Slow call | 60 s, 100% | 1 s, 50% | A default breaker never noticed a 3-second dependency |
| Open for | 60 s | 5 s | A 40-second failure became a minute of "menu unavailable" |
| Retries | 3, fixed 500 ms | 3, exponential from 100 ms, full jitter, transient errors only | Refusals (open breaker, full bulkhead) are never retried |
| Bulkhead | 25 concurrent calls, no wait | Sized per dependency by Little's law (32 here), no wait | Spring's `@ConcurrencyLimit` blocks by default and queued about 4,000 checkout callers; FoodFleet uses `REJECT` there too |
