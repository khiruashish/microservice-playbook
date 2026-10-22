# Chapter 74 — Maturity Model & Migration Roadmap

Code and measurements for [chapter 74](../../../docs/governance-org/ch74-maturity-migration-roadmap.md), on a real
monolith: Broadleaf Commerce at `broadleaf-7.1.0-GA` and ten years of its history.

| Path | What |
|---|---|
| `src/main/java/.../roadmap/` | `Contexts` (package prefixes to contexts), `Codebase` (import edges), `History` (commits as sets of contexts, Code Maat's 30-file limit), `Roadmap` (cost of a split, five extraction orders, cycles, minimum layering) |
| `src/main/resources/broadleaf-contexts.csv` | 12 candidate contexts, the storefront web layer and the platform |
| `src/test/java/` | `UnitTest` (3, tag `unit`) |
| `verify/` | `build-libs.sh` (clones Broadleaf without blobs, about 70 MB), `run.sh`, `check_apis.sh` (8 checks), `tools/` (`Lab`, `RunSuites`) |

```bash
JAVA_HOME=/path/to/jdk-25 verify/run.sh   # under a minute after the clone
```

## Findings (JDK 25.0.4, two runs, identical apart from timings)

**coupling**: 2,788 main source files, 14 contexts, 5,583 import edges between contexts (4,040 into the platform, 60 from the platform into order, payment, customer and checkout). One cycle of 9 business contexts; 12 pairs import each other. Fewest edges against any layering: 157 (217 with the platform).

**history**: 4,123 commits 2016-10-14 to the tag; 67 over 30 files, 1,570 without main sources; of 2,486 analysed, 84.6% changed one context. No context pair reached Code Maat's 30% degree (strongest admin–platform 15.2%); catalog–content 9.3% with no imports either way.

**roadmap** (after 3 / 6 extractions): fewest dependants first 224 / 500 remote edges, 5.3% / 10.5% independent changes; greedy 152 / 485, 4.9% / 11.1%; largest first 922 / 1,152, 30.9% / 45.4%; most changed first 1,083 / 1,152, 32.7% / 45.4%; value per remote edge 340 / 1,152, 31.8% / 45.4%. All 12 extracted: 1,483 remote edges, 60 inversions, 12 pairs both ways, 15.4% coordinated, 49.0% independent.

**tests**: unit 3/3. **apis**: 8 of 8 found in Code Maat 1.0.4, git 2.56.0 and Broadleaf's tag.
