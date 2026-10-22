# Chapter 49 — Distributed Tracing with OpenTelemetry

Keeping one trace per request across threads, HTTP and a message broker; sampling and propagation that services
agree on; baggage that stays where it belongs. Measured under the OpenTelemetry Java agent with a real NATS broker.

```text
src/main/java/dev/foodfleet/ch49/
├── propagation/  MessageHeaders    inject/extract context for anything with string headers (outbox rows, queues)
│                 TracedExecutors   context across hand-offs the agent doesn't cover (Context.taskWrapping, wrap)
├── baggage/      BaggagePolicy     allow-list and 512-byte cap at the edge; no baggage to third parties
└── spans/        PaymentSpans      a manual span: namespaced attributes, an event, error status, baggage copied in
src/main/resources/   application.yml (Spring Boot 4.1.1 tracing, propagation, baggage, Spring Kafka observations),
                      agent.properties (the Java agent)
src/test/java/…       12 tests
verify/
├── build-libs.sh   the OpenTelemetry API 1.66.0 and jnats 2.26.4 from their tags; agent 2.32.0, Collector 0.162.0,
│                   nats-server 2.15.1 from GitHub releases; sources for check_apis.sh
├── lib-src/        hand-written equivalents of the API's five AutoValue classes; compile-only stubs (see its README)
├── run.sh          context, messaging, sampling, baggage; then check_apis.sh
├── check_apis.sh   21 behaviours in OpenTelemetry Java, its instrumentation (Kafka, NATS, executors) and Spring Boot
└── tools/          Services (gateway, order, payment, kitchen, external partner), Lab, Measure, Json
```

## Run

```bash
mvn -B -pl chapters/ch49-distributed-tracing-otel -am verify    # from code/: compile and test
chapters/ch49-distributed-tracing-otel/verify/run.sh            # about 13 minutes; git, curl, JDK 21+, Linux x86-64
```

Kafka couldn't be run where these measurements were made (neither the broker nor kafka-clients was downloadable), so
messaging is measured on NATS, which the agent also instruments, and Kafka's behaviour is checked in the source.

## What the experiments found

Two runs on JDK 25.0.4 with the agent 2.32.0 (SDK 1.66.0), NATS 2.15.1 and the Collector 0.162.0.

| Experiment | Finding |
|---|---|
| Thread hand-offs, 200 orders | Executors, scheduled and virtual-thread executors, `CompletableFuture` and async HTTP callbacks kept the trace 200/200; `new Thread()`, `Thread.ofVirtual().start()` and an own queue lost it 200/200; `Context.wrap` fixed both. |
| NATS, 220 orders | Handler consumer: process span child of the publish span, 220/220 in the order's trace; `nextMessage()` loop 0/220; manual extraction 220/220. Publish to process 1.6–2.0 ms. |
| Sampling, 1,000 orders at 10% | Parent-based: 105–108 complete traces, nothing else. Downstream `traceidratio` 0.5: 400–401 orphan fragments; 0.05: 43–47 sampled traces without payment; `always_on`: 901–904 fragments; B3-only payment: 0 complete, 1,000 fragments; clients sending sampled `traceparent`: 1,000 traces, 10,000 spans. |
| Baggage, 50 orders | Client baggage (email, session token, 407 bytes) reached the partner on 50/50 calls; edge allow-list: 2 keys, 30 bytes; stripped for the partner call: none. `traceparent` reached the partner every time. |
| Sources | 21 of 21 checks: Kafka header propagation and process-span lifetime, NATS consumers only via `MessageHandler`, Boot's propagation defaults, `remote-fields` not an allow-list, Spring Kafka observations off by default. |
