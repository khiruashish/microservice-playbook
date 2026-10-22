---
title: "Flipkart"
description: "Preparing for Flipkart-style rounds: designing for a sale day, when traffic arrives all at once and stock is scarce, worked as an interview answer with numbers from the chapters (inventory reservations with expiry, atomic decrements, pre-scaling, shedding, hot keys and cache stampedes); the 75 e-commerce analogues the chapters end with; and the 230 questions tagged Flipkart, Myntra, Walmart or Meesho."
progress: done
---

# Flipkart

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

Indian e-commerce interviews return again and again to one scenario: **the big sale**. Traffic arrives all at once at a known minute, a few products are wanted by far more people than there is stock, and every slow or wrong answer is visible to millions. This page works through that scenario as you'd answer it in a design round, then lists the questions. Interview formats change; check the current process with your recruiter.

## The sale-day design, step by step

### 1. Name what's different about a sale

- **The load spike is scheduled.** You know the minute it starts, so you don't wait for autoscaling to notice.
- **Contention concentrates.** A few deals take most of the traffic; their stock rows and cache keys become hot spots.
- **Correctness is about scarce stock.** Overselling a ₹99 deal by 10,000 units is a bigger problem than a slow product page.

### 2. Scale before the sale, not during it

An autoscaler reacts after load arrives: in [chapter 56](../deployment/ch56-autoscaling.md), 29–34 seconds before a scale-up even started, then JVM start-up. For a sale:

- Raise minimum replicas on a schedule minutes before the start; verify the new pods are warm and have connections ([ch. 54](../deployment/ch54-kubernetes-for-spring.md#8-enterprise-case-study), [ch. 56](../deployment/ch56-autoscaling.md#8-enterprise-case-study)).
- Keep spare node capacity so new pods don't wait for nodes.
- Gate readiness on warm-up: ready-at-once pods drove an HPA to its maximum for four pods' worth of load.
- Size connection pools fleet-wide, including the surge: past about 16 connections on 2 vCPUs, PostgreSQL gained little throughput while waits inside it grew 13–17× ([ch. 33](../data/ch33-database-scaling.md)).

### 3. Reserve stock atomically, with an expiry

The core of the answer. Checkout is a saga: **reserve stock** (compensate: release), authorize payment (compensate: void), confirm with the seller (the pivot), then ship ([ch. 14](../architecture-patterns/ch14-saga-pattern.md#e-commerce-analogue-checkout-with-inventory)).

- **The reservation is one atomic, conditional update** in the stock owner's database: decrement if enough remains, in one statement. Read-modify-write loses updates: chapter 31 measured 15.5% of loyalty points lost that way ([ch. 31](../data/ch31-data-consistency.md)), and chapter 62's check-then-insert, without a primary key to stop it, redeemed a coupon twice in 172–186 of 300 concurrent rounds, while `ON CONFLICT` never did ([ch. 62](../testing/ch62-unit-slice-integration-tests.md)).
- **Reservations expire.** If a saga stalls, the stock returns to sale after N minutes, so stuck checkouts can't hold popular items.
- **Every step is idempotent** with a key from the client, because mobile retries during a sale are guaranteed ([ch. 28](../communication/ch28-idempotency-correlation.md)).
- **For the hottest deals**, split the stock across sub-counters (buckets) so updates spread over rows, or queue requests per product and hand out reservations in order. Say the trade-off: buckets can refuse a buyer while another bucket has stock; a queue adds latency but is fair.

### 4. Serve reads from caches that can't stampede

- Product pages from the CDN and edge caches with stale-while-revalidate; details from an application cache invalidated by catalogue events ([ch. 32](../data/ch32-caching-strategies.md#e-commerce-analogue-product-pages-at-peak)).
- **Prices and stock are re-read, never trusted from a cache, at add-to-cart and checkout.** Chapter 29's price sync guarded every cache write by version and served zero wrong prices through crashes ([ch. 29](../data/ch29-polyglot-persistence.md)).
- Stampede protection: one recomputation per key. Without it, one home-screen ranking expiry caused 80 recomputations at once ([ch. 32](../data/ch32-caching-strategies.md)).
- Jitter TTLs so a promotion's keys don't all expire together.

### 5. Shed the right traffic

When demand exceeds capacity despite all this, choose what fails:

- Mark routes by priority: checkout critical, browsing and recommendations sheddable. Admission control reserves capacity for critical requests; at twice capacity, it served 79–81% of checkouts against 49–50% without ([ch. 44](../resilience/ch44-timeouts-retries-backpressure.md)).
- Brown out optional features (recommendations, "people also bought") before core ones.
- A waiting room in front of the hottest deal pages turns a stampede into a queue.

### 6. Hot keys and partitions

- A topic keyed by seller or restaurant put 19–28% of orders on one of 12 partitions in [chapter 68](../performance-scalability/ch68-horizontal-scaling.md); key events by order, and salt the few hottest keys.
- Carts never live in application memory: copies of the web tier come and go during a sale ([ch. 68](../performance-scalability/ch68-horizontal-scaling.md#e-commerce-analogue-carts-at-sale-time)).

### 7. Rehearse it

Load-test the scale-out with an open (arrival-rate) model: a closed model reported a p99 of 15 ms for a service whose open-model p99 was 942 ms ([ch. 65](../testing/ch65-performance-chaos-arch-tests.md)). Freeze schema changes during the sale ([ch. 33](../data/ch33-database-scaling.md#e-commerce-analogue-the-order-table-at-sale-scale)).

## The e-commerce analogues

Every chapter ends its case study with how e-commerce applies the same idea. All 75, for revision:

--8<-- "tools/generated/interview/analogues-ecommerce.md"

## The questions

The 230 chapter questions tagged Flipkart, Myntra, Walmart or Meesho. Tags are the chapters' judgment of where a question fits, not reports of real interviews.

--8<-- "tools/generated/interview/flipkart.md"
