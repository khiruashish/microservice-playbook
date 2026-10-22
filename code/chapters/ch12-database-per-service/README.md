# Chapter 12 code — Database per service and API composition

Docs: `docs/architecture-patterns/ch12-database-per-service.md`

| Part | What it shows |
|---|---|
| `ownership/` | Audits SQL statements (e.g. exported from `pg_stat_statements`) against table ownership: who reads or writes whose tables. Bundled sample: FoodFleet's 2023 shared database |
| `composition/` | API composition done carefully (driver query, batch lookups, parallel on virtual threads, time budget, partial results) next to the naive 1 + 2N loop |
| `client/`, `api/` | The composer behind HTTP service clients and `GET /api/customers/{id}/order-history` |

```bash
cd code
mvn -B -pl chapters/ch12-database-per-service -am verify
java -cp chapters/ch12-database-per-service/target/classes dev.foodfleet.ch12.ownership.OwnershipAuditReport
java -cp chapters/ch12-database-per-service/target/classes dev.foodfleet.ch12.ownership.OwnershipAuditReport owners.txt statements.txt
```

Export your own statements from PostgreSQL (one database user per service makes `usename` the service):

```sql
SELECT usename || '|' || regexp_replace(query, '\s+', ' ', 'g')
FROM pg_stat_statements JOIN pg_user ON userid = usesysid;
```
