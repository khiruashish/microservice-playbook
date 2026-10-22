#!/usr/bin/env bash
# Chapter 32 experiments. Needs PostgreSQL 16 (trust auth for PGUSER), Redis 7 and python3.
#   PGHOST=localhost PGPORT=5432 PGUSER=postgres REDIS_PORT=6379 \
#   REDIS_RESTART="redis-cli -p 6379 shutdown nosave; redis-server --port 6379 --daemonize yes --save ''" ./run.sh
# Creates database c32 (1 M reviews, 2,000 menu cards on first run) and FLUSHES the Redis instance.
# REDIS_RESTART is used by the write-behind crash case; without it that case shows no loss.
set -euo pipefail
cd "$(dirname "$0")"
python3 experiment.py all
