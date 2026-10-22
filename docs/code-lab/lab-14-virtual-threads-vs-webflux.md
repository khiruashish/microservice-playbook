---
title: "Lab 14 — Virtual Threads vs WebFlux"
description: "The same quote endpoint, three sequential downstream calls, written as blocking Spring MVC on virtual threads and as a WebFlux chain, with a Gatling open-model load test. Then the measured part: chapter 66's four concurrency models under 250 to 2,500 quotes a second on two CPUs. Platform threads collapsed at 1,500/s (p99 5.5 s); virtual threads and the async chain both served 2,479–2,481/s at 2,500 with p99 around 310 ms; a concurrency limit sized below Little's law turned away 15,843 requests that could have been served. About an hour."
progress: done
---

# Lab 14 — Virtual Threads vs WebFlux

<div class="badges" markdown><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **You'll build** | order-service's quote endpoint twice (MVC on virtual threads, WebFlux), a downstream stand-in, and a Gatling load test |
| **Time** | About an hour, 7 minutes of it a benchmark |
| **Builds on** | [Lab 03](lab-03-two-services.md) (HTTP clients) and [lab 06](lab-06-resilience.md) (timeouts) |
| **Chapters** | [66 (latency, virtual threads, reactive)](../performance-scalability/ch66-latency-virtual-threads-reactive.md), [44 (timeouts and backpressure)](../resilience/ch44-timeouts-retries-backpressure.md), [8 (Spring Boot 4)](../foundations/ch08-spring-boot-4-essentials.md) |
| **Code** | `code/code-lab/lab-14-virtual-threads-vs-webflux/` |
| **Needs** | JDK 25 for the benchmark; Maven for the Spring modules and Gatling |

!!! note "About the lab code"
    The benchmark **was run** for this lab (`run-models.sh`, JDK 25.0.4, one 2-vCPU VM, nothing else running): it compiles and runs [chapter 66](../performance-scalability/ch66-latency-virtual-threads-reactive.md)'s four models on the JDK's HTTP server and client, which reproduce Tomcat's and Reactor Netty's thread set-ups. The Spring modules (Boot 4.1.1) and the Gatling simulation (3.16.0) are checked by import against their source, not compiled or run here ([how](index.md#how-the-lab-code-was-checked)); the Gatling DSL methods were checked in Gatling's source. `downstream/Downstream.java` runs with a bare JDK.

## The endpoint

A quote needs the menu, then a price for it, then a delivery fee for that subtotal: three calls of about 50 ms, each depending on the last. 150 ms of every request is waiting.

=== "Spring MVC, virtual threads"

    ```java title="quote-mvc/…/QuoteController.java"
    --8<-- "code/code-lab/lab-14-virtual-threads-vs-webflux/quote-mvc/src/main/java/dev/foodfleet/lab14/mvc/QuoteController.java:10:31"
    ```

    ```yaml title="quote-mvc/…/application.yml"
    --8<-- "code/code-lab/lab-14-virtual-threads-vs-webflux/quote-mvc/src/main/resources/application.yml"
    ```

=== "WebFlux"

    ```java title="quote-webflux/…/QuoteController.java"
    --8<-- "code/code-lab/lab-14-virtual-threads-vs-webflux/quote-webflux/src/main/java/dev/foodfleet/lab14/webflux/QuoteController.java:12:36"
    ```

The MVC version reads top to bottom, debugs with an ordinary stack trace, and uses the same `RestClient` as every other lab. With `spring.threads.virtual.enabled`, a request waiting on a call holds a virtual thread, a few kilobytes of heap, rather than one of Tomcat's 200 OS threads. The WebFlux version gets the same economy from a handful of event-loop threads, at the price of a different programming model in which nothing may block.

## A limit on work in progress

```java title="quote-mvc/…/ConcurrencyLimit.java"
--8<-- "code/code-lab/lab-14-virtual-threads-vs-webflux/quote-mvc/src/main/java/dev/foodfleet/lab14/mvc/ConcurrencyLimit.java:13:41"
```

A thread pool was also a limit. Virtual threads remove it, so past capacity an unbounded service accepts everything and queues it in memory. The filter puts a limit back, explicitly, and answers `503` with `Retry-After` beyond it ([ADR-066](../performance-scalability/ch66-latency-virtual-threads-reactive.md#8-enterprise-case-study)).

## Load: an open model

```java title="load-test/…/QuoteSimulation.java"
--8<-- "code/code-lab/lab-14-virtual-threads-vs-webflux/load-test/src/test/java/dev/foodfleet/lab14/QuoteSimulation.java:17:47"
```

`constantUsersPerSec` starts new requests at a fixed rate however slow the server gets, as customers do. A closed model (a fixed number of users in a loop) waits for each response before sending the next, so a slow server automatically receives less load and the queue that real traffic would build never appears in the results.

## Measured: four models, rising load

`run-models.sh` runs chapter 66's quote in four models against a 50 ms backend, 20 seconds per rate, everything on the same two CPUs:

```console
$ JAVA_HOME=/path/to/jdk-25 code/code-lab/lab-14-virtual-threads-vs-webflux/run-models.sh
model             offered   served/s    p50 ms    p99 ms    max ms  rejected   failed OS threads     peak RSS
platform              250        248     153.6     185.9       238         0        0        225       219 MB
platform             1000        993     161.0     249.9       298         0        0        225       290 MB
platform             1500       1175    2939.9    5512.2      5556         0        0        225       607 MB
platform             2500        579    4813.3   14702.1     16122         0    32851        225       657 MB
virtual               250        248     152.3     161.6       215         0        0         25       187 MB
virtual              1000        993     152.8     204.3       276         0        0         25       202 MB
virtual              1500       1489     155.7     221.8       281         0        0         25       305 MB
virtual              2500       2479     189.0     306.8       396         0        0         25       393 MB
async                 250        248     152.7     169.2       200         0        0         25       186 MB
async                1000        992     152.8     192.5       221         0        0         25       230 MB
async                1500       1489     155.7     208.5       235         0        0         25       279 MB
async                2500       2481     187.0     316.8       406         0        0         25       385 MB
virtual-limited       250        248     152.2     158.6       176         0        0         25       182 MB
virtual-limited      1000        992     152.3     161.1       182         0        0         25       198 MB
virtual-limited      1500       1483     155.4     204.9       251       112        0         25       284 MB
virtual-limited      2500       1695     164.0     238.9       284     15843        0         25       326 MB
```

**Platform threads hit a wall that has nothing to do with CPU.** 200 threads each held for 150 ms can finish at most about 1,333 requests a second (200 ÷ 0.15). At 1,500/s the queue grew for the whole run: p50 went from 161 ms to 2.9 seconds, and at 2,500/s a third of requests timed out.

**Virtual threads and the async chain behaved alike**, up to 2,500/s with a p99 around 310 ms, on 25 OS threads. For I/O-bound services the choice between them isn't throughput; it's the programming model and what your team can debug.

**The limit was set too low.** In progress = rate × time in the system (Little's law): 2,500/s × 0.19 s is about 470 requests, and the limit was 300, so 15,843 requests that the machine could have served got a 503. Size the limit from the rate you must serve and the latency at that rate, and revisit it when either changes.

### Compare with chapter 66

Chapter 66's two runs on the same kind of VM agree on everything up to 1,500/s. They differ at 2,500/s: there, virtual and async served only 190–839/s and failed 43,000–46,500 requests, the async server ran out of memory, and the limited server was the only one still answering in time (1,510–1,588/s, p99 261–303 ms). At that rate the machine handles 10,000 HTTP exchanges a second for server, backend and load generator together, which chapter 66 notes is past what two CPUs reliably do; this run's VM had a little more headroom. The lesson holds either way: **the unbounded models fail all at once when they cross capacity, and the limit is what keeps a service answering past it**, provided it's sized above normal peak, not at it.

## Run it

```bash
code/code-lab/lab-14-virtual-threads-vs-webflux/run-models.sh                  # the benchmark, ~7 min

java code/code-lab/lab-14-virtual-threads-vs-webflux/downstream/Downstream.java &   # menu, price and fee on 8095
cd code/code-lab/lab-14-virtual-threads-vs-webflux
(cd quote-mvc && mvn spring-boot:run &) ; (cd quote-webflux && mvn spring-boot:run &)
cd load-test && mvn gatling:test -DbaseUrl=http://localhost:8094     # then -DbaseUrl=http://localhost:8096
```

Run the load generator on a different machine from the services if you can; on the same one, it competes for the CPUs it's measuring.

## Check yourself

- [ ] You can explain the platform model's ceiling of about 1,333 requests a second from two numbers.
- [ ] You can size `max-in-progress` for 2,500 quotes a second at a 190 ms p50, with headroom.
- [ ] Gatling's report for quote-mvc and quote-webflux at 1,500/s shows similar latency, and neither fails its assertions.

## Break it

!!! question "Exercises"
    1. Turn `spring.threads.virtual.enabled` off in quote-mvc and run Gatling. At which stage does the p99 assertion fail?
    2. Add a `Thread.sleep(50)` inside the WebFlux chain's `map`. What happens to latency for every request, and why does it hurt WebFlux more than MVC?
    3. Raise `max-in-progress` until the limited server rejects nothing at 2,500/s here. What's the smallest value that works, and how does it compare with Little's law?
    4. Replace `constantUsersPerSec` with `rampConcurrentUsers` (a closed model). How do the platform model's results at 1,500/s change, and which version tells the truth about customers?

**Next:** [Lab 15](lab-15-kubernetes-helm-keda.md) deploys a service with the shared Helm chart and watches it autoscale.
