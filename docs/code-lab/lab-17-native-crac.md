---
title: "Lab 17 — Native Image vs CRaC"
description: "Shorten menu-service's start four ways and measure what you can: on JDK 25 the same service reached its first 200 in a median 725–745 ms, 457–544 ms with an AppCDS archive and 359–384 ms with an AOT cache trained by 3,000 requests. A cache that no longer matches its jars is silently ignored unless AOTMode=on makes the start fail. Then the builds for a GraalVM native executable and a CRaC checkpoint, with chapter 60's measurements and why FoodFleet hasn't adopted CRaC. About an hour."
progress: done
---

# Lab 17 — Native Image vs CRaC

<div class="badges" markdown><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **You'll build** | menu-service's start measured plain, with AppCDS and with an AOT cache; the image builds for an AOT cache, a native executable and a CRaC checkpoint |
| **Time** | About an hour |
| **Builds on** | [Lab 02](lab-02-persistence.md) (the service) and [lab 15](lab-15-kubernetes-helm-keda.md) (scaling out, where start time is paid) |
| **Chapters** | [60 (native images, CRaC, startup)](../deployment/ch60-native-images-startup.md), [53 (containers)](../deployment/ch53-containerization.md), [56 (autoscaling)](../deployment/ch56-autoscaling.md) |
| **Code** | `code/code-lab/lab-17-native-crac/` |
| **Needs** | JDK 25 for `verify/startup.sh`; GraalVM 25 or Docker for the native build; a CRaC JDK and CRIU-capable nodes for CRaC |

!!! note "About the lab code"
    `verify/startup.sh` **was run**, twice, on JDK 25.0.4 on the host (one 2-vCPU VM, no container): it builds [chapter 60](../deployment/ch60-native-images-startup.md)'s menu-service (plain Java with Logback, Micrometer and the Prometheus client, the libraries a Boot service starts with) and times 10 starts per variant. The native and CRaC builds weren't run here: GraalVM wasn't installed for this lab, and CRIU fails in these VMs. Their figures are chapter 60's, measured in containers.

## Why start time matters

A pod can't take traffic until it's started and warm. Start time decides how fast an HPA's new pods help at the lunch peak ([lab 15](lab-15-kubernetes-helm-keda.md)), how long a rollout takes, and whether scale-to-zero is tolerable. Four ways to shorten it, from least to most invasive:

| | What it saves | What it costs |
|---|---|---|
| **AppCDS** | Parsing and verifying classes: an archive of them, written by a training run | A training run in the build; the archive must match the JDK and class path |
| **AOT cache** (JDK 24+, JEPs 483, 514, 515) | The same, plus classes already linked and method profiles, so the JIT starts informed | The same; JDK 25's `-XX:AOTCacheOutput` does it in one step |
| **Native image** (GraalVM) | Nearly all of it: a compiled executable, no class loading, no JIT warm-up | A 3-minute build with 1.7 GB of memory; reflection needs hints; lower peak throughput |
| **CRaC** | Everything up to the checkpoint: the JVM resumes with Spring started and the JIT warm | A JDK with CRaC, CRIU with privileges, and a heap image that contains whatever secrets were in memory |

## 1. Measure on your own JVM

```bash title="verify/startup.sh (the three runs)"
--8<-- "code/code-lab/lab-17-native-crac/verify/startup.sh:23:36"
```

`StartTimer` starts the process and polls every 2 ms until the first `200`, so the number includes everything a load balancer waits for. Two runs:

```console
$ LIBS=<the service's jars> code/code-lab/lab-17-native-crac/verify/startup.sh
openjdk version "25.0.4" 2026-07-21 LTS
training with -XX:AOTCacheOutput: 9065 ms; cache 33 MB, AppCDS archive 14 MB
jvm     median 745 ms (min 633, max 858) over 10 starts
appcds  median 457 ms (min 378, max 626) over 10 starts
aot     median 384 ms (min 318, max 496) over 10 starts
```

| Variant | Run 1 | Run 2 |
|---|---|---|
| Plain JVM | 745 ms | 725 ms |
| AppCDS | 457 ms | 544 ms |
| AOT cache | 384 ms | 359 ms |

The AOT cache halved the start, for a 33 MB file and a 9-second training run in the build. Chapter 60 measured the same ranking in containers at 1 CPU (1,139–1,246, 753–761 and 611–732 ms) and a bigger gap at 0.25 CPU, where the plain JVM took 4.3 seconds and the cache 2.0: the less CPU a pod gets, the more a cache matters ([ADR-060](../deployment/ch60-native-images-startup.md#8-enterprise-case-study)).

**Train through the real path.** `Trainer` sends 3,000 requests over HTTP. Chapter 60's cache trained without the HTTP path saved about half as much: classes the training never loads aren't in the cache.

## 2. A cache that doesn't match is ignored, quietly

An AOT cache is only valid for the exact JDK and class path it was trained with, down to the jars' timestamps. Copying the jars (a new timestamp) and starting again:

```console
$ java -XX:AOTCache=app.aot -cp "app2/*" -version          # default: AOTMode=auto
[0.023s][warning][aot] This file is not the one used while building the AOT cache: 'app2/prometheus-metrics-1.7.0.jar', timestamp has changed
[0.023s][error  ][aot] shared class paths mismatch (hint: enable -Xlog:class+path=info to diagnose the failure)
# exit status 0: the JVM started, without the cache
$ java -XX:AOTMode=on -XX:AOTCache=app.aot -cp "app2/*" -version
# exit status 1: the start fails
```

By default the JVM logs and carries on at full start time, so a build change that invalidates the cache shows up only as slower pods. FoodFleet's CI starts every image once with `-XX:AOTMode=on`, which fails instead (ADR-060). Train in the same image stage that ships, so the class path can't drift.

## 3. The image builds

```dockerfile title="images/Dockerfile.aot"
--8<-- "code/code-lab/lab-17-native-crac/images/Dockerfile.aot"
```

`spring.context.exit=onRefresh` makes the training run exit as soon as the context has refreshed: no load generator needed, at the cost of not caching the request path.

=== "Native image"

    ```bash title="images/build-native.sh"
    --8<-- "code/code-lab/lab-17-native-crac/images/build-native.sh"
    ```

=== "CRaC"

    ```bash title="images/crac-checkpoint.sh"
    --8<-- "code/code-lab/lab-17-native-crac/images/crac-checkpoint.sh"
    ```

## What chapter 60 measured

The same service in containers (1 CPU, 512 MB, median of 10 starts, two runs):

| Image | Size | First 200 | At 0.25 CPU | Idle memory | Requests/s after a minute |
|---|---|---|---|---|---|
| JVM | 125 MB | 1,139–1,246 ms | 4,275–4,394 ms | 40–42 MiB | 1,466–1,564 |
| JVM + AOT cache | 165 MB | 611–732 ms | 2,043–2,091 ms | 42 MiB | 1,421–1,600 |
| Native image | 50.5 MB | 246–266 ms | 280–289 ms | 7.5–7.8 MiB | 994–1,026 |

The native image wins every column but the last: after a minute of load it served about a third fewer requests a second, because GraalVM CE's ahead-of-time compiler can't use the run-time profiles the JIT has. CRaC wasn't measured: CRIU couldn't initialise in the VM.

## Choosing

| Workload | FoodFleet's choice (ADR-060) |
|---|---|
| Long-running JVM services that scale out at peaks | AOT cache, trained through the HTTP API, checked with `AOTMode=on` |
| Functions, jobs, scale-to-zero services | Native image, tested as the executable |
| Anything | Not CRaC, until it runs on FoodFleet's nodes |

## Check yourself

- [ ] `startup.sh` shows the AOT cache starting in roughly half the plain JVM's time on your machine.
- [ ] You can explain why a cache trained without HTTP saves less.
- [ ] You can say what a CRaC checkpoint image contains that a container image normally doesn't.

## Break it

!!! question "Exercises"
    1. Train the AOT cache with `Trainer` changed to send no requests (start and stop the server only). How much of the saving is left?
    2. Run the `aot` variant with `taskset -c 0` (one CPU), then plain. Does the gap grow, as chapter 60's 0.25-CPU column suggests?
    3. In `Dockerfile.aot`, train in one stage and copy the jars to the final stage with a fresh timestamp. Start it with `-XX:AOTMode=on`. What happens, and how do you fix the Dockerfile?
    4. Build lab 01's service as a native image and run its tests natively (`mvn -PnativeTest test`). Does anything fail that passed on the JVM? If so, which hint fixes it?

**Next:** [Lab 18](lab-18-multi-tenancy.md) isolates tenants inside one service.
