#!/usr/bin/env bash
# Runs all five chapter 29 experiments. Needs PostgreSQL 16+ (contrib: pg_trgm, cube, earthdistance) and
# Redis 7, psql, pgbench, redis-cli, redis-benchmark and python3 (standard library only).
#   PGHOST=localhost PGPORT=5432 PGUSER=postgres REDIS_PORT=6379 ./run.sh
# Creates (and drops/recreates objects in) database $PGDATABASE (default c29); FLUSHES the Redis instance.
set -euo pipefail
cd "$(dirname "$0")"
export PGDATABASE=${PGDATABASE:-c29}
psql -XAtq -d postgres -c "SELECT 1 FROM pg_database WHERE datname = '$PGDATABASE'" | grep -q 1 \
  || psql -Xq -d postgres -c "CREATE DATABASE $PGDATABASE"
echo "== 1. menus: documents vs tables";            ./e1_document_vs_relational.sh 2>&1 | grep -v NOTICE
echo; echo "== 2. dish search";                       python3 e2_search.py
echo; echo "== 3. rider locations";                   ./e3_rider_locations.sh 2>&1 | grep -v NOTICE
echo; echo "== 4. position history";                  ./e4_time_series.sh 2>&1 | grep -v NOTICE
echo; echo "== 5. keeping two stores in sync";        python3 e5_keeping_stores_in_sync.py
