---
title: "Elasticsearch / OpenSearch"
description: "Search engines FoodFleet hasn't adopted yet (ADR-029): dish search runs on PostgreSQL full-text with a trigram fallback, which found 29 of 29 test queries at a 14 ms median over 192,000 items, with typos costing 43–57 ms. What a search engine adds (relevance tuning, synonyms, facets, analyzers per language, millisecond typo search at scale), the signals that would justify one, and how it would be fed: a derived index from change events with versions, never dual writes (71–77 items left wrong in chapter 29)."
progress: done
---

# Elasticsearch / OpenSearch

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | Distributed search engines built on Apache Lucene: inverted indexes, analyzers, BM25 relevance, aggregations, near-real-time indexing. **OpenSearch** is the AWS-led fork of Elasticsearch 7.10, now under the Linux Foundation |
| **FoodFleet uses** | **Not adopted.** Chapter 12 assumed Elasticsearch for search; ADR-029 measured PostgreSQL first and kept search there until the signals below appear |
| **Decided in** | [ADR-029](../data/ch29-polyglot-persistence.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 29](../data/ch29-polyglot-persistence.md), [chapter 12](../architecture-patterns/ch12-database-per-service.md) (search as a derived read model) |
| **Related** | [PostgreSQL](postgresql.md), [Debezium](debezium-cdc.md), [Outbox](outbox-implementation.md), [Logging stack](logging-stack.md) (OpenSearch for logs) |

## What PostgreSQL already did

[Chapter 29](../data/ch29-polyglot-persistence.md) ran 29 queries a customer might type against 192,000 menu items, each with the dishes it should find:

| Strategy | Typo | Half-typed | Found | p50 |
|---|---|---|---:|---:|
| `LIKE '%q%'`, trigram index | 0/7 | 5/5 | 12/29 | 0.7–0.8 ms |
| Full-text, ranked | 0/7 | 0/5 | 17/29 | 5–6 ms |
| Trigram similarity | 7/7 | 5/5 | 27/29 | 55–61 ms |
| **Words first, then similar names** (the service) | **7/7** | 5/5 | **29/29** | **14 ms** |

- A trigram index fixes `LIKE`'s speed, not its answers.
- The stemmer broke prefixes ("chicken biry" found nothing) until the partial word was also matched as typed.
- Typo matching is the expensive part: **43–57 ms at the median, up to ~88 ms**, and some trigram queries took 380–434 ms on their own.

## When a search engine earns its operating cost

From chapter 29: when search is a product feature, not a lookup.

| Signal | Why PostgreSQL struggles |
|---|---|
| **Relevance tuning**: boosting by popularity, distance, availability; learning to rank | `ts_rank` is basic; tuning means hand-written SQL |
| **Synonyms**: "paneer" for "cottage cheese" | Possible with thesaurus dictionaries, awkward to manage |
| **Facets and aggregations** over the results | Each facet is another query |
| **Many languages**, Indic scripts and transliteration | Analyzers per language are an engine feature |
| **Typo-tolerant typeahead in milliseconds** over millions of documents | Trigram similarity grows with the data |

Each also brings a cluster to run: shards and replicas, JVM heap, index lifecycle, snapshots, upgrades, security, and a sync path.

## Feeding an index

A search index is always a **derived read model**, rebuilt from the source of truth:

1. Services publish changes through the [outbox](outbox-implementation.md), or [Debezium](debezium-cdc.md) reads them from the WAL.
2. An indexer consumes them and writes documents with **external versioning** (`version_type=external`), so an older event can't overwrite a newer one.
3. A full reindex into a new index behind an **alias** handles mapping changes; the alias switches when the new index has caught up.

Chapter 29 measured the alternative for a Redis copy, and it holds for an index too: **dual writes** left 71–77 of 5,000 items wrong; an outbox projector that claims rows and checks versions left **0**.

## Choosing between them

| | Elasticsearch | OpenSearch |
|---|---|---|
| Licence | Elastic License / SSPL, and AGPL as an option since 2024 | Apache 2.0 |
| Managed | Elastic Cloud | Amazon OpenSearch Service, others |
| Java client | Elasticsearch Java client; Spring Data Elasticsearch | OpenSearch Java client; Spring Data OpenSearch (community) |

APIs diverged after 7.10; pick one and use its own client rather than assuming compatibility.

## Pitfalls

- **Search as the source of truth**: it is near-real-time, not transactional.
- **Dynamic mappings in production**: a field guessed as `text` that should have been `keyword` needs a reindex.
- **Too many shards**: each one costs heap; size shards by data volume, not by habit.
- **Deep pagination with `from`/`size`**: use `search_after` with a point in time.
- **Adding it before measuring the database**: FoodFleet's first instinct, five stores for a team of eight.
