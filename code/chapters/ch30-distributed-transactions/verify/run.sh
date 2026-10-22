#!/usr/bin/env bash
# Chapter 30 experiments. Needs two PostgreSQL 16 clusters with max_prepared_transactions > 0 (trust auth for
# PGUSER) and python3; B_STOP / B_START / B_RESTART must be shell commands that control the second cluster, e.g.
#   PGHOST_A=localhost PGPORT_A=5432 PGHOST_B=localhost PGPORT_B=5433 PGUSER=postgres \
#   B_STOP="pg_ctl -D /data/b -m fast -w stop" B_START="pg_ctl -D /data/b -l /data/b/log -w start" \
#   B_RESTART="pg_ctl -D /data/b -m fast -w restart" ./run.sh
# Creates databases c30a and c30local on cluster A and c30b on cluster B, and drops/recreates their tables.
set -euo pipefail
cd "$(dirname "$0")"
python3 experiment.py all
