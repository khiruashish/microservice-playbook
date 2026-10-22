---
title: "Distributed Scheduling"
description: "Scheduled work when every service runs several replicas: a @Scheduled method in a Deployment runs once per pod, so FoodFleet uses Kubernetes CronJobs for batch work, ShedLock (or a leader) for jobs that live in a service, and fencing where a second run would do harm. Chapter 15's outbox relay as the case: one active relay per service or partitioning by aggregate, because SKIP LOCKED alone published one order's events out of order. Plus Quartz clustering, JobRunr and workflow-engine timers."
progress: done
---

# Distributed Scheduling

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | Running work at times or intervals in a system with many copies: Kubernetes **CronJobs**, **ShedLock** (a lock row or key around Spring's `@Scheduled`), **Quartz** in clustered mode, JobRunr, leader election, and workflow-engine timers |
| **FoodFleet uses** | CronJobs for batch work; ShedLock or a single active instance for in-service schedules such as the outbox relay; a scheduled job that raises HPA minimums before meal peaks |
| **Decided in** | [Chapter 4](../foundations/ch04-twelve-factor.md) (factor VIII), [ADR-015](../architecture-patterns/ch15-outbox-inbox-cdc.md#8-enterprise-case-study), [ADR-056](../deployment/ch56-autoscaling.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 4](../foundations/ch04-twelve-factor.md), [chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md), [chapter 68](../performance-scalability/ch68-horizontal-scaling.md) |
| **Related** | [Distributed locks](distributed-locks.md), [Outbox](outbox-implementation.md), [Temporal & Camunda](temporal-camunda.md), [Kubernetes](kubernetes.md) |

## The problem

Factor VIII assumes replicas are interchangeable, so an in-process scheduler multiplies: scale to 5 pods and a nightly job runs 5 times. [Chapter 4](../foundations/ch04-twelve-factor.md)'s answer: a Kubernetes CronJob, a distributed lock (ShedLock) with fencing where correctness matters, or a dedicated single-replica worker.

## Choosing a mechanism

| Mechanism | Runs | Good for | Watch out for |
|---|---|---|---|
| **Kubernetes CronJob** | A new pod per run | Batch jobs, reports, cleanups | `concurrencyPolicy: Forbid`; missed runs (`startingDeadlineSeconds`); a JVM start per run |
| **ShedLock** around `@Scheduled` | In whichever replica gets the lock | Short periodic tasks inside a service | `lockAtMostFor` must exceed the longest run, or two run at once; the lock is a lease, not a guarantee |
| Leader election (Kubernetes Lease, Spring Integration leader) | Continuously in one replica | Pollers that should run all the time | Failover gap; a paused leader still believes it leads |
| Quartz, clustered (JDBC job store) | One node per trigger | Many dynamic schedules, misfire handling | Its own tables and locking |
| Workflow-engine timers | Inside a durable workflow | "Remind the restaurant in 2 hours" | An engine to run |

## The outbox relay: once, in order

[Chapter 15](../architecture-patterns/ch15-outbox-inbox-cdc.md) runs the outbox relay as a polling publisher (`FOR UPDATE SKIP LOCKED`). With several instances each polling with `SKIP LOCKED`, one could publish order o-1's second event before another published its first. The rule: **one active polling relay per service** (leader election, or a lock such as ShedLock), or partition the outbox by aggregate-id hash across instances. CDC with Debezium avoids the poller entirely ([Debezium](debezium-cdc.md)).

## When a second run would do harm

A lock's lease can expire while its holder is paused; then two run. [Lab 13](../code-lab/lab-13-redis.md) measured it for a Redis lock under GC pauses: releasing with `DEL` lost 213–229 of 300 updates; compare-and-delete still lost 94–103; a **fencing token** checked by the resource lost none. For jobs whose effects must happen once (charging, payouts), make the job's writes idempotent or fenced; the scheduler alone can't promise once ([Distributed locks](distributed-locks.md)).

## Pitfalls

- **`@Scheduled` in a Deployment with replicas > 1**.
- **`lockAtMostFor` shorter than the job**.
- **CronJobs with `concurrencyPolicy: Allow`** (the default) for jobs that mustn't overlap.
- **Time zones**: CronJob schedules use the controller's zone unless `timeZone` is set.
- **Schedules as hidden load**: a job at :00 in every service; spread them.
