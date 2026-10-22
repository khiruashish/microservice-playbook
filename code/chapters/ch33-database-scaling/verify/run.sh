#!/usr/bin/env bash
# Chapter 33 experiments. Needs PostgreSQL 16 with max_connections >= 300 (trust auth for PGUSER), pgbench, psql
# and python3. Takes about 25 minutes; creates databases c33, c33pool and c33svc.
#   PGHOST=localhost PGPORT=5432 PGUSER=postgres ./run.sh
set -euo pipefail
cd "$(dirname "$0")"
echo "== E1 pool size";        ./e1_pool_size.sh
echo; echo "== E2 DDL under load"; python3 e2_ddl_under_load.py
echo; echo "== E3 backfill";       python3 e3_backfill.py
echo; echo "== E4 rename";         python3 e4_rename.py
echo; echo "== E5 sharding";       python3 e5_sharding.py
echo; echo "== service SQL";       python3 check_service_sql.py
