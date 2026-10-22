# Chapter 67 — JVM Tuning in Containers

Code and measurements for [chapter 67](../../../docs/performance-scalability/ch67-jvm-tuning-containers.md).

| Path | What |
|---|---|
| `src/main/java/.../ergonomics/` | `WhatTheJvmSees`: the CPUs and memory the JVM sees, its collector, `MaxHeapSize`, GC and ForkJoin thread counts |
| `src/main/java/.../service/` | `MenuCache` (180,000 menus of 20 items: a ~350 MB live set of small objects), `MenuService` (open load inside the process, latency from due time, CPU and peak RSS), `GcEvents` (JFR `jdk.GarbageCollection` and `jdk.EvacuationFailed` through a `RecordingStream`) |
| `src/main/java/.../memory/` | `NativeMemory`: Native Memory Tracking's summary through the DiagnosticCommand MBean |
| `src/test/java/` | `UnitTest` (4, tag `unit`) |
| `verify/` | `build-libs.sh`, `run.sh`, `check_apis.sh` (18 checks), `tools/` (`Lab`: one Docker container per measurement; `RunSuites`) |

```bash
JAVA_HOME=/path/to/jdk-25 verify/run.sh   # about 15 minutes; needs Docker
```

The JDK is mounted read-only into a minimal image (`C67_IMAGE`, default `ff-jre:25`; any image with glibc works).

## Findings (JDK 25.0.4, one 2-vCPU VM, two runs)

**ergonomics** (identical in both runs): 0.5–1 CPU → 1 processor, Serial; 1.5 CPUs → 2, G1; 2 CPUs with 1 GiB → **Serial, 256 MB heap**; 2 CPUs with 1,792 MiB or more → G1. Max heap 25% of the container (126 MB of 256 MiB: MinRAMPercentage). `ActiveProcessorCount=2` on 1 CPU → G1. ForkJoin common pool: 1 thread everywhere.

**gc**: 6,000 requests/s, 30 s after 10 s warm-up, 2 GiB container, `-Xmx1g`.

| Collector | p99 | p99.9 | Pauses (30 s) | Longest | CPU | Peak RSS |
|---|---|---|---|---|---|---|
| Serial | 380–450 ms | 629–681 ms | 1,118–1,207 ms | 655–704 ms | 0.51–0.54 | 1,028–1,032 MB |
| Parallel | 8–22 ms | 34–97 ms | 383–565 ms | 58–87 ms | 0.49–0.50 | 948–950 MB |
| G1 | 37 ms | 74–95 ms | 611–622 ms | 85–112 ms | 0.55–0.56 | 949–962 MB |
| G1 + compact headers | 20–33 ms | 60–83 ms | 493–645 ms | 69–107 ms | 0.54–0.57 | 836–841 MB |
| ZGC | 1.0–1.1 ms | 3.8–9.5 ms | 1 ms | 0.1 ms | 0.70–0.72 | 1,312–1,329 MB |
| Shenandoah | 30–36 ms | 39–46 ms | 5 ms | 0.8–1.0 ms | 0.69–0.70 | 1,100–1,108 MB |
| G1, 1 CPU | 68–92 ms | 126–172 ms | 834–951 ms | 148–196 ms | 0.52–0.53 | 941–970 MB |
| ZGC, 1 CPU | 106–136 ms | 144–162 ms | 1 ms | 0.1 ms | 0.66–0.68 | 1,289–1,299 MB |

Live set 348–352 MB (303 MB with compact headers; ZGC reports 412–414 MB used after `System.gc()`).

**heap** (1 GiB container, G1): 25% → `OutOfMemoryError` at startup (exit 3); 50% → p99 588–697 ms, 3 full GCs; 75% → p99 496–630 ms, 2 full GCs; 90% → p99 25–35 ms, no full GCs, peak RSS 894–946 MB; 75% + 150 MB direct buffers → p99 488–650 ms; 90% + 150 MB direct → **OOMKilled (exit 137) in run 1**, survived in run 2 at 1,044 MB peak RSS.

**nmt** (2 GiB, `-Xmx1g`, committed MB): G1 heap 870–881, GC 54, total 976–987, peak RSS 944–957; ZGC heap 958–960, **GC 238–266**, total 1,252–1,279, peak RSS 1,296–1,317.

**jfr** (G1): CPU 0.54–0.55 cores with GC events only; 0.56–0.58 with a continuous `default` recording; 0.58–0.59 with `profile`. p99 30–42 ms throughout.

**tests**: unit 4/4. **apis**: 18 of 18 found in JDK 25.0.4's sources.

## Notes

- Peak RSS is the process's `VmHWM`, which also counts file-backed pages (the mounted JDK's libraries) that the kernel can reclaim under pressure, so a peak slightly above the limit, as in run 2 (1,044 MB on 1,024 MiB), doesn't by itself mean the limit was crossed.
- G1's full collections at 50% and 75% followed evacuation failures (12–16 `jdk.EvacuationFailed` events per run); the GC log showed `Pause Young (Mixed) ... (Evacuation Failure: Allocation)` followed by `Pause Full (G1 Compaction Pause)`.
- Shenandoah's pauses don't explain its p99; the likely cause is pacing (allocation stalls when the collector falls behind), which `jdk.GarbageCollection` doesn't record.
- The lab names the collectors from its `-XX:+Use...GC` options; the published run printed ZGC as "Z", which `Lab.java` now prints as "ZGC".
