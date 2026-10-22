#!/bin/bash
# Usage: PGHOST=... PGPORT=... PGUSER=... ./experiment.sh   (needs CREATE DATABASE rights)
# Runs every strategy: 200 payment keys, each requested 4 times concurrently (a client retry plus
# duplicates), 16 sessions at a time, against PostgreSQL 16. Prints charges at the provider and rows.
cd "$(dirname "$0")"
PSQL="psql -X"
$PSQL -q -f schema.sql 2>/dev/null
rm -f provider_D.log
printf "%-34s %9s %13s %11s %8s  %s\n" strategy charges "keys >1 charge" "payment rows" errors responses
for s in a_no_key b_check_then_insert c_idempotency_key d_serializable e_unique_constraint; do
  rm -f errors.log fails.log
  ./run.sh $s.sql 200 4
  S=$(echo $s | cut -c1 | tr a-z A-Z)
  fails=$( [ -f fails.log ] && wc -l < fails.log || echo 0)
  if [ "$S" = D ]; then
    charges=$(wc -l < provider_D.log); twice=$(sort provider_D.log | uniq -d | wc -l)
  else
    charges=$($PSQL -d idem -tA -c "SELECT count(*) FROM provider_charges WHERE strategy='$S'")
    twice=$($PSQL -d idem -tA -c "SELECT count(*) FROM (SELECT 1 FROM provider_charges WHERE strategy='$S' GROUP BY idem_key HAVING count(*)>1) x")
  fi
  if [ "$S" = E ]; then rows=$($PSQL -d idem -tA -c "SELECT count(*) FROM payments_unique");
  else rows=$($PSQL -d idem -tA -c "SELECT count(*) FROM payments WHERE strategy='$S'"); fi
  resp=$($PSQL -d idem -tA -c "SELECT coalesce(string_agg(outcome || '=' || n, ', ' ORDER BY outcome), '-') FROM (SELECT outcome, count(*) n FROM outcomes WHERE strategy='$S' GROUP BY outcome) x")
  printf "%-34s %9s %13s %11s %8s  %s\n" $s $charges $twice $rows $fails "$resp"
done
