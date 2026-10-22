---
title: "Runbooks"
description: "What on-call does when FoodFleet's alerts fire: the four SLO burn alerts generated from ADR-051, stuck sagas, dead letters and consumer lag, a lost zone or region, Redis, OpenBao or admission control down, OOM kills, aborted canaries and an exhausted error budget. Each runbook says what users see, what to check first, what to do and what never to do, drawn from the chapters' decisions and measurements."
progress: done
---

# Runbooks

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

Every FoodFleet alert links a runbook ([ADR-051](../observability/ch51-slo-alerting.md)). These are the platform's: the ones for the [generated SLO alerts](observability.md#from-objectives-to-alerts), and the ones for failures the chapters planned for. A service's own runbooks, for its own dependencies and data, live in its repository.

Each runbook follows the same shape: **what users see**, **check first**, **act**, and what **not** to do, because the instinctive action is often the harmful one.

!!! tip "Before anything else"
    1. **What changed?** Deploys, flag and config changes are events beside the metrics ([ADR-047](../observability/ch47-observability-fundamentals.md)). Most incidents follow a change.
    2. **Which journey, which hop?** Find a slow or failed trace for the journey; tail sampling keeps every error and every trace over 300 ms. The trace names the hop.
    3. **Roll back first, debug second.** A rollback is a revert in the environment repository; Argo CD applies it within one sync interval ([ADR-058](../deployment/ch58-cicd-gitops.md)).

## SLO alerts

### Checkout availability is burning its budget

`CheckoutAvailabilityErrorBudgetBurn` · page at 14.4× or 6× burn, ticket at 3× or 1×.

- **Users see:** "Couldn't place your order" on some attempts. Payment, kitchen and delivery problems don't cause this alert: they're reached through the saga after the order is placed.
- **Check first:** checkout has four required services ([resilience](resilience.md#what-fails-when-a-service-is-down)): `edge-gateway`, `customer-bff`, `order-service`, `pricing-service`. Which one returns the errors? Then: a recent deploy of any of the four; order-service's database (connections, lock waits); pricing-service's own errors, since checkout can't proceed without a price.
- **Act:** roll back the recent change. If pricing-service is down, that's the outage; nothing upstream can work around it ([ADR-016](../architecture-patterns/ch16-strangler-fig-migration.md)). If the database is the cause, see [zone](#a-zone-is-lost) or [region](#a-region-is-lost).
- **Don't:** scale order-service out while the database is the bottleneck. Chapter 33 measured little gain past about 16 connections on 2 vCPUs and 13–17× longer waits inside the database; more pods mean more connections.

### Checkout latency is burning its budget

`CheckoutLatencyErrorBudgetBurn` · more than 1% of successful checkouts over 300 ms.

- **Users see:** a slow "Place order" button.
- **Check first:** the slow traces' longest span. Usual suspects: pricing-service (cache misses after a deploy or a Redis restart), order-service's database, or one bad pod. If one pod is slow, outlier detection in the waypoint should eject it ([ADR-057](../deployment/ch57-service-mesh.md)); check that it did.
- **Act:** roll back, or drain the bad pod. If a dependency is slow, the deadline budgets ([ADR-044](../resilience/ch44-timeouts-retries-backpressure.md)) cap the damage; check that admission control is rejecting rather than queueing.
- **Don't:** raise timeouts. Longer timeouts turn a slow dependency into a queue of waiting requests.

### Browse availability is burning its budget

`BrowseAvailabilityErrorBudgetBurn`

- **Users see:** menus that don't load, or "This restaurant doesn't deliver to you" when it does.
- **Check first:** `menu-service` and `zone-service`, browse's two required services behind the edge. Is it peak? Under overload, browsing is shed first by design ([ADR-044](../resilience/ch44-timeouts-retries-backpressure.md)); a burn at dinner peak with checkout healthy may be the shedding working.
- **Act:** if shedding: raise minimum replicas now rather than waiting for the HPA (scale-up took 29–34 s to start in chapter 56), and fix the scheduled pre-peak minimums afterwards. Otherwise, roll back.
- **Don't:** reclassify browse routes as critical to stop the shedding. That moves the overload onto checkout.

### Tracking availability is burning its budget

`TrackingAvailabilityErrorBudgetBurn` · objective 99.5%.

- **Users see:** a frozen rider on the map, or no map.
- **Check first:** tracking-gateway instances at their 20,000-stream limit (they answer 503 beyond it); a deploy draining streams; Redis, which carries the per-order channels to the instance holding each stream ([ADR-026](../communication/ch26-websockets-sse.md)).
- **Act:** add instances; if a deploy is draining, let it finish (60 s per batch).
- **Don't:** restart all instances at once. Every client reconnects together; the random 1–10 s retry exists to spread that, and a mass restart defeats it.

## Messaging

### Stuck sagas

Alert on the age of sagas in `STUCK`.

- **Users see:** "Confirming with the restaurant" for minutes, or nothing if the saga is past the pivot.
- **Check first:** which step. Before the pivot (coupon, payment authorization, kitchen acceptance), a participant is down or slow. After it (capture, rider, approval), the food is being cooked.
- **Act:** fix the participant; the retry job resumes the sagas. Chapter 14's 41 stuck sagas all completed once their outage passed. A capture that keeps failing after the pivot goes to the Payments team.
- **Don't:** compensate a saga past the pivot. The kitchen has started cooking; refunds after that are a separate, reviewed flow ([order lifecycle](order-lifecycle.md#open-questions)).

### A message in a dead-letter topic

Every DLT message pages the owning team during business hours ([ADR-025](../communication/ch25-asynchronous-messaging.md)).

- **Check first:** the error attached to the message. A validation failure is a producer bug or a schema change a tolerant reader should have absorbed ([ADR-027](../communication/ch27-api-contracts.md)); anything else is the consumer's.
- **Act:** fix, deploy, then replay with the DLT tool. Consumers are idempotent through their inbox, so a replay is safe.
- **Don't:** skip ordered messages to clear the backlog. Ordered consumers back off for up to 31 s before dead-lettering precisely so nothing overtakes a failing record; replay in order.

### Consumer lag growing

Alert after two minutes of growth ([ADR-025](../communication/ch25-asynchronous-messaging.md)).

- **Check first:** lag per partition. One hot partition means a skewed key; all partitions means the consumer is slow or stopped.
- **Act:** for a slow sink, check that it batches (one transaction per poll, [ADR-069](../performance-scalability/ch69-kafka-throughput-tuning.md)). For slow per-record work, key-ordered concurrency inside the consumer.
- **Don't:** add consumers beyond the partition count; chapter 69's 12 consumers on 6 partitions left 6 idle.

## Infrastructure

### A zone is lost

- **Users see:** a brief blip; Tier 0 data fails over automatically, in about 0.1 s for promotion, with nothing lost ([ADR-046](../resilience/ch46-disaster-recovery.md)).
- **Check:** the synchronous standby in the third zone is healthy; Tier 0 now runs with one synchronous partner left.
- **If a second zone goes:** Tier 0 writes stop, by design: `ANY 1` of two zones can't be satisfied. An operator decides between waiting and accepting asynchronous commits; that decision trades durability for availability and belongs to the incident commander.

### A region is lost

Regional failover is never automatic ([ADR-046](../resilience/ch46-disaster-recovery.md)): from one region, a dead region and a broken link look the same, and promoting during a partition forks the order history (chapter 46 measured 17,000–22,700 commits in conflict after a 2-second partition).

1. **Decide** it's a loss, not a partition: the incident commander, with evidence from outside both regions.
2. **Fence** the old region: stop its writers and withdraw its DNS. Promotion before fencing is the one irreversible mistake.
3. **Promote** the standby in ap-south-2.
4. **Clients reconnect** on their own: they list every host with `targetServerType=primary`. Clients that follow DNS instead took 24–33 s on a 30 s TTL.
5. **Expect loss** up to the cross-region replication lag (85–278 commits in chapter 46's test). The daily reconciliation with the payment provider ([ADR-034](../data/ch34-reporting-analytics.md)) finds payments without orders.

### Redis is down

- **Users see:** mostly nothing for caches, which are optional: reads go to the database ([ADR-032](../data/ch32-caching-strategies.md)). Prices are read from PostgreSQL; rankings serve their last copy.
- **But:** rate limits fall back to each instance enforcing its share, and an alert fires ([ADR-040](../security/ch40-api-security.md)); live tracking loses its per-order channels; view counts since the last flush (up to about a second) are lost.
- **Act:** restore Redis; nothing needs rebuilding by hand, since every copy is refilled from its source.

### OpenBao is unreachable

- **Users see:** nothing at first. Running pods hold database credentials for up to an hour, renewable to 24 ([ADR-039](../security/ch39-secrets-management.md)).
- **But:** new pods can't start (no credentials), so scale-outs and deploys fail; pause deploys.
- **Act:** restore OpenBao within the credentials' lifetime. Chapter 39 measured renew-only credentials failing at `max_ttl`; FoodFleet renews and then rotates, so running pods survive as long as OpenBao is back before they need new ones.

### Admission control is down

Kyverno's failure policy is Fail, so nothing new is admitted to production namespaces ([ADR-041](../security/ch41-supply-chain-compliance.md)).

- **Act:** restore Kyverno. For an emergency fix meanwhile, the break-glass procedure, with the change committed to Git within a day ([ADR-058](../deployment/ch58-cicd-gitops.md)).
- **Don't:** switch the failure policy to Ignore. That admits unsigned images for as long as nobody remembers to switch it back.

## Releases and capacity

### A pod was OOMKilled

- **Check first:** with `ExitOnOutOfMemoryError` the JVM exits on a heap OOM with a Java error in the log; an OOMKill without one means non-heap memory grew past the limit ([ADR-053](../deployment/ch53-containerization.md), [ADR-067](../performance-scalability/ch67-jvm-tuning-containers.md)).
- **Act:** compare native memory tracking with the service's recorded totals; dump the continuous JFR recording with `jcmd <pid> JFR.dump` from a sibling pod before it's replaced. Raise memory (request and limit together) only once the cause is known.
- **Don't:** raise the heap percentage. In chapter 67, a 90% heap in 1 GiB was OOMKilled as soon as 150 MB of direct buffers were added.

### A canary was aborted

- **Users see:** almost nothing: a canary stopped a release failing 5% of requests after 7–10 failures, where a rolling update failed 944–953 ([ADR-058](../deployment/ch58-cicd-gitops.md)).
- **Check:** the analysis that failed: error rate or p99 against the stable version. With one canary pod, the analysis has little data; chapter 58 saw a healthy canary aborted on latency.
- **Act:** fix and release again. If the abort was noise, raise the step's minimum request count rather than loosening the threshold.

### The error budget is spent

ADR-051's policy, agreed with product:

- **Half the budget spent:** releases need a rollback plan and a second reviewer.
- **All of it:** releases freeze except fixes, until the 30-day window recovers.

The freeze is the policy working, not a punishment: it moves the next weeks' effort from features to whatever spent the budget.
