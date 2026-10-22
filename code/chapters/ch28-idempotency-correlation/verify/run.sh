#!/bin/bash
# usage: run.sh <script.sql> <keys> <duplicates>
# Runs every request as its own psql session, 16 at a time. Connection from PGHOST/PGPORT/PGUSER.
PSQL="psql -d idem -q -X"
for k in $(seq 1 $2); do for d in $(seq 1 $3); do echo "key-$k"; done; done | \
  xargs -P 16 -I{} sh -c "$PSQL -v k={} -f $1 >/dev/null 2>>errors.log || echo FAIL >> fails.log"
