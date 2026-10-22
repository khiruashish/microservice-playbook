# Chapter 75 — Multi-Tenancy, Multi-Region & Compliance

Code and measurements for [chapter 75](../../../docs/governance-org/ch75-multi-tenancy-multi-region-compliance.md), on PostgreSQL 16.

| Path | What |
|---|---|
| `src/main/java/.../tenancy/` | `Cluster` (a throwaway PostgreSQL instance), `TenancyModel` (shared table + forced RLS, schema per tenant, database per tenant), `TenantScope` (the tenant set per transaction) |
| `src/main/java/.../erasure/` | `CryptoShredder` (per-subject AES-256-GCM keys in a separate store), `ByteScan` (where erased bytes survive) |
| `src/test/java/` | `UnitTest` (3, tag `unit`; H2 as the key store) |
| `verify/` | `build-libs.sh`, `run.sh`, `check_apis.sh` (7 checks), `tools/` (`Lab`, `RunSuites`) |

```bash
JAVA_HOME=/path/to/jdk-25 verify/run.sh   # about 7 minutes, 2 GB of disk; needs PostgreSQL 16 (PG_BIN)
```

## Findings (JDK 25.0.4, PostgreSQL 16.13, two runs; leaks and erasure identical)

**models** (2,000 orders per tenant): per-tenant provisioning shared 0.3–0.9 ms, schema 5.6–10.4 ms, database 52–105 ms; 100 tenants 35.8 / 43.1 / 811.1 MB and 423 / 1,013 / 41,900 relations; 1,000 tenants migrated in 0.8–1.1 s (shared) and 3.2–3.3 s (schemas); last-20-orders p99 0.64–1.42 ms in every model; 100 connections for 100 tenant databases; opening one 4.6–7.4 ms p50.

**leaks**: a forgotten tenant predicate returned 10,000 rows from 20 tenants without RLS, as owner (unforced) and as superuser; 500 from 1 with forced RLS. Pool of 4, 193 forgotten requests: per-session SET 177 saw another tenant; per transaction 0 (193 errors with a plain cast, 193 empty with NULLIF).

**noisy**: report alone 133–135 ms; small tenants' p99 3.8–4.8 ms quiet, 25–38 ms with 6 concurrent reports, 8.4–10.6 ms with a bulkhead of 1 (72–78 reports vs 81), 34–40 ms with a 100 ms statement timeout (0 completed, 525–529 cancelled).

**erasure**: plaintext email after DELETE: table 1, WAL 2, backup 1; after VACUUM and VACUUM FULL: table 0, WAL 2, backup 1. Encrypted: 0 everywhere; after key deletion (1.4–2.0 ms) decrypt returns nothing; the key remained once in the key store's WAL.

**tests**: unit 3/3. **apis**: 7 of 7 found in PostgreSQL 16.13's documentation.
