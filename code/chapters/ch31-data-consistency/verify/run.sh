#!/usr/bin/env bash
# Chapter 31 experiments. Needs a PostgreSQL 16 primary with two asynchronous streaming replicas (hot standby),
# trust authentication for PGUSER, and python3. The script changes recovery_min_apply_delay on the replicas
# (ALTER SYSTEM) and resets it afterwards; it creates database c31 on the primary.
#   PGHOST=localhost PGUSER=postgres PRIMARY_PORT=5432 REPLICA1_PORT=5433 REPLICA2_PORT=5434 ./run.sh
set -euo pipefail
cd "$(dirname "$0")"
python3 experiment.py all
