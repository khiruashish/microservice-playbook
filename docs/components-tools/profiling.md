---
title: "Profiling: JFR & Pyroscope"
description: "Continuous profiling at FoodFleet: JDK Flight Recorder on every JVM with its default settings (ADR-047), cheap enough to leave on (4–7% CPU, about a second of startup), and profiles sent to a profile store such as Pyroscope. Measured in chapter 47: a payment span with no children and a few ms of its own; the profile put 63–64% of the CPU in one method, a PBKDF2 call copied from login code. How to read profiles (inclusive time, flame graphs), and when async-profiler adds something."
progress: done
---

# Profiling: JFR & Pyroscope

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | **JDK Flight Recorder (JFR)**: the JVM's built-in, low-overhead event recorder (CPU samples, allocations, locks, GC, I/O), read with JDK Mission Control or `jfr`. **Pyroscope** (Grafana): a continuous-profiling store that ingests profiles, including JFR, and shows flame graphs over time. **async-profiler**: a sampling profiler without safepoint bias |
| **FoodFleet uses** | JFR on every JVM with default settings; profiles sent to a profile store |
| **Decided in** | [ADR-047](../observability/ch47-observability-fundamentals.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 47](../observability/ch47-observability-fundamentals.md), [chapter 67](../performance-scalability/ch67-jvm-tuning-containers.md) (JVM in containers) |
| **Related** | [OTel Collector & LGTM](otel-collector-lgtm.md), [APM tools](apm-tools.md), [Distributed tracing](distributed-tracing.md) |

## Traces show time, profiles show code

[Chapter 47](../observability/ch47-observability-fundamentals.md): payment-service 1.6 derived each payment's idempotency key with PBKDF2 (4,000 iterations), copied from the login code.

| What the trace showed | |
|---|---|
| `POST /payments` p50 / p99 | 7.0–7.4 ms / 13.3–13.9 ms |
| Child spans under it | **0 of 1,800** |
| payment-service CPU | 3.4 ms per payment |

| What the profile showed (share of samples with the method on the stack) | |
|---|---:|
| `Services.payment` | 73–75% |
| **`Services.idempotencyKey`** | **63–64%** |
| `SecretKeyFactory.generateSecret` | 61–63% |

The trace said the time was spent inside the service, with nothing to say on what. The profile named the method. Its "hottest methods" view listed only `MessageDigest` and `PBKDF2KeyImpl` internals: **read profiles by inclusive time or as a flame graph** to get from library frames to the code that called them.

## Cheap enough to leave on

| Setup (3 thin JVMs) | p99 | CPU per 1,000 orders | Startup |
|---|---:|---:|---:|
| No instrumentation | 16.8–16.9 ms | 3,387–3,531 ms | 1.1 s |
| **JFR, default settings** | 15.2–16.2 ms | 3,593–3,673 ms (+4–7%) | 2.0–2.1 s |
| JFR, profile settings | 15.4–17.6 ms | 3,638–3,742 ms | 2.0 s |

Compare the OpenTelemetry agent's 38–56%. JFR always on means the profile from *before* the incident exists.

## In practice

```text
# start with the JVM (rolling files, kept for a bounded size/age)
-XX:StartFlightRecording=settings=default,disk=true,maxage=6h,maxsize=512m,dumponexit=true

# on a running JVM
jcmd <pid> JFR.dump name=1 filename=/tmp/recording.jfr
jfr print --events jdk.ExecutionSample /tmp/recording.jfr | head
```

| Tool | Use |
|---|---|
| JDK Mission Control | Deep analysis of one recording: hot methods, allocation, locks, GC |
| Pyroscope / Grafana | Profiles over time across the fleet; compare before and after a deploy; link from a trace span to its profile |
| async-profiler | CPU without safepoint bias, native frames, wall-clock mode for blocked threads; flame graphs directly |
| `jcmd Thread.dump_to_file -format=json` | Virtual-thread-aware thread dumps |

## Pitfalls

- **Reading self time only**: the culprit is the caller of the library frame.
- **Profiling only when something's wrong**: the problem is gone by the time you attach.
- **Profile settings everywhere**: more events, more overhead; default settings continuously, profile settings on demand.
- **Unbounded recordings on disk** in containers with small ephemeral storage.
- **Recordings treated as harmless**: like heap dumps, they can contain arguments, paths and environment; store them as sensitive.
