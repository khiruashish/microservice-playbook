---
title: "Architecture Decision Records"
description: "All 77 of FoodFleet's ADRs in one log, collected from the chapters by a script so the log can't drift from them: grouped by part, each with its context, decision and consequences, plus what a lint of the log finds (11 records dated out of order and one amended in place) and how a real team would keep such a log."
progress: done
---

# Architecture Decision Records

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

Every chapter ends with one of FoodFleet's architecture decisions. This page collects all 77 in one place. They aren't copied by hand: `adrs.py` reads every `!!! decision` block from the chapters and writes the log below, so a chapter's ADR and this page always say the same thing.

```bash
python3 code/foodfleet/model/adrs.py
# 77 ADRs, 2024-01-22 to 2026-08-25; 11 out of order, 1 amended in place
```

Each record has the format [chapter 73](../governance-org/ch73-adrs-fitness-functions.md) recommends: a status and a date, the **context** (what forced the decision, with the chapter's measurements), the **decision**, and its **consequences**, good (✅) and bad (⚠️).

## How to use this log

- **To find why something is the way it is**, search this page for the technology or the service; the context says what was measured.
- **To challenge a decision**, read its context first. If the measurement no longer holds (new versions, a different load), that's the case for a new decision.
- **To change a decision**, write a new ADR that supersedes it. Accepted records are never edited ([ADR-073](#adr-073)).
- **To see which decisions are checked by code**, see [chapter 73](../governance-org/ch73-adrs-fitness-functions.md)'s fitness functions and [chapter 72](../governance-org/ch72-platform-engineering.md)'s platform checks. Most decisions still have none.

## What a lint of this log finds

FoodFleet's ADRs are numbered by chapter, not by the date they were decided, and the chapters were written in topic order. So the numbers and the dates disagree. `adrs.py` keeps the longest run of records whose dates rise with their numbers, the same rule as chapter 73's lint, and lists the rest:

--8<-- "code/foodfleet/model/generated/adr-dates.md"

What this shows:

- **The story starts in the middle.** Numbers 12 to 77 run in date order, from January 2024 to April 2026. Most of Part 1's decisions (ADR-001 to ADR-006, and ADR-008) are dated 2026, while decisions that build on them, such as ADR-012's database per service, are dated from 2024. Chapter 1's history, in which FoodFleet is still a modular monolith in the first half of 2026, agrees with the Part 1 dates, so it's the later records' dates that don't fit the story.
- **One record was edited after acceptance.** ADR-007 carries an amendment date instead of a new record that amends it.
- **The rule chooses between equals.** ADR-011 and ADR-012 can't both be in the run; `adrs.py` keeps ADR-012 and flags ADR-011, while chapter 73's lint, run when the log had 72 records, flagged ADR-012. Either way, 11 records are out of order.

The handbook leaves these as they are, as a working example of what the lint is for. A real team would fix the cause, not the dates:

1. Number decisions when they're made, not by topic.
2. Never edit an accepted record; supersede or amend it with a new one that links back.
3. Run the lint in CI, so the next out-of-order record fails a build instead of waiting for someone to notice.

## The log

Click a record to open it. Links in a record go to the chapter that made the decision.

--8<-- "code/foodfleet/model/generated/adr-log.md"
