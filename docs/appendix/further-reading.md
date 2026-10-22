---
title: "Books, Papers & Talks"
description: "Every work the 77 chapters recommend, collected into one list by part with the chapters that cite each, generated from the chapters' further-reading sections: 357 distinct books, papers, specifications and documentation pages, the 15 cited most across chapters, and a shortlist of where to start."
progress: done
---

# Books, Papers & Talks

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

Each chapter ends with the sources it relied on. This page puts them together: `tools/reading.py` reads every chapter's further-reading section, merges works cited by more than one chapter, and lists them by part.

```bash
python3 tools/reading.py
# 357 distinct works, 42 cited by more than one chapter
```

## Where to start

If you read five things beyond this handbook, the chapters point most often to these:

- **Sam Newman, *Building Microservices***, for the whole territory, and his *Monolith to Microservices* for migration.
- **Chris Richardson, *Microservices Patterns***, the pattern language most of Part 2 uses.
- **Martin Kleppmann, *Designing Data-Intensive Applications***, for replication, consistency and streams: the foundations under Parts 4 and 10.
- **Matthew Skelton and Manuel Pais, *Team Topologies***, for the organisational half.
- **Michael Nygard, *Release It!***, for how systems fail in production, and what to do about it.

## Cited by the most chapters

--8<-- "tools/generated/reading-most-cited.md"

## Everything, by part

Each entry once, under the part that cites it first, with every chapter that cites it.

--8<-- "tools/generated/reading.md"
