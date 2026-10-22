---
title: "Interview Prep Overview"
description: "How to prepare for microservice interviews with this handbook: 927 questions from the 77 chapters, sorted by band and by the kind of company they fit, 235 incident drills, four tested coding answers, five system design walkthroughs, revision plans and a last-night cheat sheet, all generated from or linked to the chapters, so every answer has its measurement behind it."
progress: done
---

# Interview Prep Overview

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

Every chapter of this handbook ends with twelve interview questions and short answers. This section collects them, adds the round types the chapters can't cover one at a time, and gives you a plan. Nothing here is a separate body of knowledge: every answer links back to the chapter where it was built and measured, because in an interview the measurement is what makes an answer yours.

## What's here

| Page | What it gives you |
|---|---|
| [Revision plans](revision-plans.md) | Two-week and six-week plans by band |
| [Last-night cheat sheet](cheat-sheet.md) | Ten rules, the numbers worth quoting, every part on one table |
| [L1](band-l1.md) · [L2](band-l2.md) · [L3](band-l3.md) · [L4](band-l4.md) | Every question at that band, with short answers |
| [Amazon](amazon.md) | Leadership Principles stories, scale design, 233 questions |
| [Flipkart](flipkart.md) | The sale-day design, the e-commerce analogues, 230 questions |
| [Goldman Sachs & JP Morgan](goldman-sachs-jpmorgan.md) | Consistency, audit, payments; the fintech analogues; 278 questions |
| [SAP](sap.md) | Tenancy, extensibility, integration; 185 questions |
| [Cognizant, TCS, Infosys, Accenture](service-companies.md) | Eight client scenarios; 183 questions |
| [System design rounds](system-design-rounds.md) | Food delivery, ride matching, payments, notifications, a rate limiter |
| [Troubleshooting rounds](troubleshooting-rounds.md) | A method, the common symptoms, 235 incident drills |
| [Coding rounds](coding-rounds.md) | Circuit breaker, rate limiter, idempotent consumer, saga coordinator, tested |
| [Architect & leadership rounds](architect-leadership-rounds.md) | Migration, build vs buy, org design, governance, cost |

## The questions, by part and band

--8<-- "tools/generated/interview/counts.md"

A question can carry more than one band, so the band columns add up to more than the total. Two thirds of the questions are L3: the chapters are written for people who already build services and want to choose well between patterns.

## About the company tags

Each chapter's author tagged its questions with the kinds of company or round they fit. These are editorial judgments about where a topic tends to come up, **not reports of questions asked in real interviews**, and companies change their processes. The most-used tags:

--8<-- "tools/generated/interview/tags.md"

## How to use this section

1. **Pick your band and a plan** from [revision plans](revision-plans.md).
2. **Read the chapters' sections 4 and 9**: the measurements and the questions. Section 4 gives you the numbers; section 9 makes you say them.
3. **Answer out loud** before opening a question's short answer. Then follow the link for the full one.
4. **Add the company page** for the interview you're preparing for.
5. **The night before**, read the [cheat sheet](cheat-sheet.md), then stop.

## Regenerating

The question lists, drills, analogue tables and counts are generated from the chapters:

```bash
python3 tools/interview.py
# 235 incident drills
# 927 questions from 77 chapters
```

Every question's link was checked against the built site: 927 of 927 anchors resolve.
