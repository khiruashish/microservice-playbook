# Chapter 73 — ADRs, Fitness Functions & Tech Radar

Code and measurements for [chapter 73](../../../docs/governance-org/ch73-adrs-fitness-functions.md).

| Path | What |
|---|---|
| `src/main/java/.../adr/` | `DecisionLog`: reads Nygard (adr-tools), MADR and this handbook's ADR blocks; checks L1–L7 (sections, status, numbering, date, chronology, references, supersession); which ADRs code names |
| `src/main/java/.../fitness/` | `Bytecode` (ClassFile API: dependencies, public API), `Radar` (Build Your Own Radar CSV), `Probe` (a release in its own JVM: startup, open-model latency), `FitnessFunction`, `Functions` (F1–F5, each naming its ADR) |
| `src/main/resources/orders/` | order-service's sources, changed release by release by the lab |
| `src/main/resources/radar/` | `foodfleet-radar.csv` (19 blips, 4 on hold), `radar-prefixes.csv` |
| `src/test/java/` | `UnitTest` (3, tag `unit`) |
| `verify/` | `build-libs.sh`, `run.sh`, `check_apis.sh` (8 checks), `tools/` (`Lab`, `RunSuites`) |

```bash
JAVA_HOME=/path/to/jdk-25 verify/run.sh   # about 20 minutes; needs git
```

## Findings (JDK 25.0.4, two runs; log results identical)

**log**: adr-tools 3.0.0's own log 9 of 9 clean; MADR 4.0.0's own 0 of 19 (18 without status, 1 "on hold", none dated; both optional in MADR); this handbook's 72 ADRs 60 clean: 11 dated out of order (ADR-001 to 010 and 012), ADR-023's text cites ADR-057 (decided 14 months later). Code named 7 of the 72 before this chapter.

**train**: eight releases, five functions (F1 ADR-009 domain independence, F2 radar Hold, F3 ADR-027 API compatibility, F4 ADR-060 startup ≤ 1 s, F5 ADR-066 p99 ≤ 20 ms at 200/s).
r2 F3 (a record component removed the old constructor; accepted), r3 F1 (domain → adapter), r4 F2 (`SimpleDateFormat`, `Date`), r5 F3 (`cancel` removed), r6 F5 (p99 55.6–68.9 ms), r7 F4 (1,315–1,460 ms; F5 in 1 run of 2), r1 and r8 clean. Static functions ≤ 4.8 ms each; dynamic 0.33–1.46 s launch + 7.0–7.4 s load.

**noise**: 12 runs per build; baseline p99 4.03 / 4.98 ms. Fixed 20 ms budget caught the fourfold regression (scan 20,000, p99 19.4–19.6 ms) in 3 of 12 single runs and 0 of 4 best-of-threes; +10% raised 3–9 false alarms in 12 on unchanged code; +50% best of three: 0 false alarms, 4 of 4 caught.

**tests**: unit 3/3. **apis**: 8 of 8 found in adr-tools 3.0.0, MADR 4.0.0, Build Your Own Radar 1.2.1 and the JDK 25 ClassFile API.
