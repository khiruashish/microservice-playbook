#!/usr/bin/env bash
# Chapter 34 experiments. Needs two PostgreSQL 16 clusters (trust auth for PGUSER): the services' cluster with
# wal_level = logical, and a reporting cluster; pgbench, taskset and python3. ~12 minutes. For the CPU split in E2,
# start the services' cluster on CPU 0 and the reporting cluster on CPU 1 (taskset -c 0 / -c 1 pg_ctl start).
#   SRC_PGHOST=... SRC_PGPORT=... RPT_PGHOST=... RPT_PGPORT=... PGUSER=postgres ./run.sh
# Recreates databases restaurants_svc, orders_svc and payments_svc (services) and reporting, federated, c34e5, c34svc.
set -euo pipefail
cd "$(dirname "$0")"
echo "== set-up"
python3 -c "import common, time; t = time.time(); common.build_sources(force=True); \
print(f'services built in {time.time() - t:.0f} s'); \
print(f'reporting copy by logical replication: initial copy {common.build_reporting(force=True):.1f} s')"
echo; echo "== E1 cross-service report";   python3 e1_cross_service_report.py
echo; echo "== E4 materialized views";     python3 e4_materialized_views.py
echo; echo "== E5 incremental correctness"; python3 e5_incremental_correctness.py
echo; echo "== E2 reports on the primary"; python3 e2_reports_on_primary.py
echo; echo "== E3 CDC lag and slots";      python3 e3_cdc_lag.py
echo; echo "== service SQL";               PGHOST=$RPT_PGHOST PGPORT=$RPT_PGPORT python3 check_service_sql.py
