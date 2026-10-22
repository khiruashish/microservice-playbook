---
title: "Notification Gateways"
description: "SMS, push, email and WhatsApp at FoodFleet: notifications are a generic subdomain (buy providers, wrap them behind an anti-corruption layer), notification-service is a platform service that sends while each domain owns its templates (its backlog dropped 70%), sending happens asynchronously from events so a slow SMS gateway can't slow checkout, and duplicates are stopped by a shared dedup store plus the order's last-notified version (an in-memory set let 2,513 of 3,000 rebalance duplicates through after deploys). Push for backgrounded apps, SSE for the open tracking screen."
progress: done
---

# Notification Gateways

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | Providers that deliver messages to people: SMS aggregators, push services (FCM for Android, APNs for iOS), email (SES, SendGrid and others), WhatsApp Business; plus the internal service that chooses channel, renders, sends, retries and records delivery |
| **FoodFleet uses** | notification-service as a platform service consuming events, sending through bought providers behind an ACL; domains own templates; SSE for live tracking, push when the app is in the background |
| **Decided in** | [Chapter 5](../foundations/ch05-domain-driven-design.md) (generic subdomain), [ADR-028](../communication/ch28-idempotency-correlation.md#8-enterprise-case-study) (deduplication), [ADR-026](../communication/ch26-websockets-sse.md#8-enterprise-case-study) (live updates) |
| **Learn it in** | [Chapter 6](../foundations/ch06-service-decomposition.md), [chapter 28](../communication/ch28-idempotency-correlation.md), [chapter 1](../foundations/ch01-architecture-styles.md) |
| **Related** | [Kafka](kafka.md), [Idempotency store](idempotency-store.md), [WebSockets & SSE](websockets-sse.md), [Resilience4j](resilience4j.md) |

## Buy, wrap, keep business logic out

[Chapter 5](../foundations/ch05-domain-driven-design.md) classifies notifications as a **generic** subdomain: SMS and push providers, bought and wrapped. [Chapter 6](../foundations/ch06-service-decomposition.md)'s incident: notification-service owned every template (confirmations, rider alerts, refund emails, marketing); every team queued behind its backlog and a refund-copy change waited three weeks. The fix made it a platform service that **sends**; each domain owns its templates and decides what to say. The backlog dropped 70%.

## Never on the request path

[Chapter 1](../foundations/ch01-architecture-styles.md): a 3-second sleep in a synchronous `orderConfirmed` notification made checkout three seconds slower "because of an SMS". Notifications are triggered by events (`OrderPlaced`, not `SendSmsEvent`: name facts, [chapter 19](../architecture-patterns/ch19-event-driven-architecture.md)), and a provider outage delays messages without failing orders. In sagas, a sent SMS can't be compensated: it goes after the pivot ([chapter 14](../architecture-patterns/ch14-saga-pattern.md)).

## Exactly one message, in practice

Kafka delivers at least once; deploys rebalance partitions; DLT replays are routine. [Chapter 28](../communication/ch28-idempotency-correlation.md)'s incident: after each deploy, a few thousand customers got "Your order is on the way" twice, because dedup was an in-memory set per instance (2,513 of 3,000 rebalance duplicates slipped through). The fix: a shared dedup store with a 1-hour window, and the order row's "last notified version" so replays are no-ops ([Idempotency store](idempotency-store.md)). Pass an idempotency key to providers that accept one.

## Channels

| Situation | Channel |
|---|---|
| Tracking screen open | SSE from the tracking-gateway ([WebSockets & SSE](websockets-sse.md)) |
| App in the background | Push (FCM/APNs) |
| No app, or must arrive (OTP, delivery failed) | SMS, with a fallback provider |
| Receipts, refunds | Email |

## Running the gateway

- **Per-provider circuit breakers and bulkheads**, with a fallback provider for critical messages ([Resilience4j](resilience4j.md)).
- **Rate limits per provider**, and backpressure to the consumer rather than dropped messages.
- **Delivery receipts** (provider webhooks) stored per message; idempotent webhook handling.
- **Personal data**: phone numbers and tokens are masked in logs ([Logging stack](logging-stack.md)); opt-outs and quiet hours enforced in one place.
- **Regulation**: SMS in India requires DLT registration of sender IDs and templates; marketing needs consent.

## Pitfalls

- **Synchronous sends** in a request.
- **Business logic and templates centralised** in the notification team.
- **Per-instance deduplication**.
- **Commands disguised as events** (`SendSmsEvent`).
- **No fallback** for OTPs and critical messages.
