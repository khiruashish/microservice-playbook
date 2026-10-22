# Chapter 46 — Disaster Recovery & Multi-Region

How much FoodFleet loses, and for how long, when a machine, a zone or a region goes away; measured on real
PostgreSQL 16 clusters.

```text
src/main/java/dev/foodfleet/ch46/
├── objectives/  Tier                 RPO, RTO, replication and rehearsal interval per recovery tier
├── cell/        CellRouter            tenants to cells by rendezvous hashing; pin, drain, undrain
│                ShuffleSharder        each tenant's own k of n instances
└── failover/    FailoverController    probe, declare dead after n failures, fence, promote; a timeline of each step
                 PrimaryLocator        find the primary by asking each host pg_is_in_recovery()
src/main/resources/
├── application-dr.yml                 multi-host pgJDBC URL, targetServerType=primary, bounded timeouts
└── postgres/                          orders-primary.conf (quorum sync, WAL archiving), orders-dr-standby.conf
src/test/java/…   11 tests
verify/
├── build-libs.sh   PostgreSQL REL_16_13, pgJDBC REL42.7.13 and Toxiproxy v2.12.0 sources; the Toxiproxy binary
├── run.sh          rpo, rto, restore, cells (any subset); then check_apis.sh
├── check_apis.sh   14 settings and defaults in PostgreSQL's, pgJDBC's and Toxiproxy's sources
└── tools/          Lab.java (clusters, standbys, Toxiproxy links), Pg.java (wire-protocol client), Measure.java
```

## Run

```bash
mvn -B -pl chapters/ch46-disaster-recovery -am verify     # from code/: compile and test
chapters/ch46-disaster-recovery/verify/build-libs.sh      # sources for check_apis.sh, Toxiproxy binary
PG_BIN=/usr/lib/postgresql/16/bin chapters/ch46-disaster-recovery/verify/run.sh   # about 12 minutes
```

`run.sh` needs PostgreSQL 16 server binaries (`initdb`, `pg_ctl`, `pg_basebackup`, `pg_receivewal`, `pgbench`) and a
JDK 21+. Run as root, the PostgreSQL commands run as the `postgres` user. Clusters live in `/tmp/c46pg`
(`-Dc46.dir=...` to move them) and are deleted between experiments.

## What the experiments found

Two runs on JDK 25.0.4, PostgreSQL 16.13 and Toxiproxy 2.12.0, all clusters on one 2-vCPU container; distance is
latency added to the replication link.

| Experiment | Finding |
|---|---|
| Replication and data loss | Synchronous standbys lost nothing in every row; in another zone a commit took 1.3–1.7 ms against 0.9–1.0 ms asynchronous; across a 20 / 60 ms round trip, ~23 / ~63 ms, and 8 clients managed 338–351 / 112–126 commits a second instead of about 7,000. Asynchronous lost 0–7 commits when only the primary crashed, 85–278 when the region was lost, 17,000–22,700 when the link failed 2 s before the primary. |
| Failover | Detection was interval × threshold (3.1 / 15.1 / 30.1 s); promotion 0.1 s; clients checking each host's role wrote again 0.3 s after promotion, clients following a 30 s DNS TTL 24–33 s after the crash. |
| Restores | `archive_timeout = 60 s` lost 298–317 of ~1,450 orders, 10 s lost 99–119, `pg_receivewal --synchronous` 0. Restoring 157 MB + 185 MB WAL took about 2 s, 755 MB + 500 MB WAL about 5 s. |
| Cells | One poison tenant: 100% of tenants down in a shared pool of 8, 24.8% with 4 cells, 12.7% with 8, 3.6% with shuffle shards of 2 of 8, 1.7% with 3 of 8 (identical in both runs). |
| Sources | All 14 checks passed: `archive_timeout` defaults to 0, `recovery_target_action` to `pause`; pgJDBC's `targetServerType` to `any`, `hostRecheckSeconds` 10, `socketTimeout` 0, `connectTimeout` 10 s. |

Fencing, cross-region DNS and object-storage restores were not measured (no cloud here); the chapter says where those
add to the numbers above. Full tables are in the chapter.
