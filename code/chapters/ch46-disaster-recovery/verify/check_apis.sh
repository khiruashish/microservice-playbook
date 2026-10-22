#!/usr/bin/env bash
# Checks the settings and defaults chapter 46 relies on against PostgreSQL 16.13, pgJDBC 42.7.13 and Toxiproxy 2.12.0.
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
after() {   # description, file, regex of a line, regex of the line after it
  if grep -EA1 -- "$3" "$2" 2>/dev/null | tail -n 1 | grep -Eq -- "$4"; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
P=$SRC/pg
J=$SRC/pgjdbc/pgjdbc/src/main/java/org/postgresql

echo "-- PostgreSQL 16.13"
check "synchronous_commit: remote_write"                      $P/src/backend/utils/misc/guc_tables.c '\{"remote_write", SYNCHRONOUS_COMMIT_REMOTE_WRITE'
check "synchronous_commit: remote_apply"                      $P/src/backend/utils/misc/guc_tables.c '\{"remote_apply", SYNCHRONOUS_COMMIT_REMOTE_APPLY'
check "synchronous_commit defaults to on"                     $P/src/backend/utils/misc/postgresql.conf.sample '#synchronous_commit = on'
check "synchronous_standby_names empty by default (async)"    $P/src/backend/utils/misc/postgresql.conf.sample "#synchronous_standby_names = ''"
check "synchronous_standby_names: ANY n (...) quorum"         $P/doc/src/sgml/config.sgml 'ANY <replaceable class="parameter">num_sync</replaceable>'
check "archive_timeout: 0 (no forced switch) by default"      $P/src/backend/utils/misc/postgresql.conf.sample '#archive_timeout = 0'
check "recovery_target_action defaults to pause"              $P/src/backend/utils/misc/postgresql.conf.sample "#recovery_target_action = 'pause'"
check "pg_promote()"                                          $P/src/backend/access/transam/xlogfuncs.c '^pg_promote\(PG_FUNCTION_ARGS\)'

echo "-- pgJDBC 42.7.13 (Spring Boot 4.1.1)"
after "targetServerType defaults to any"                      $J/PGProperty.java '"targetServerType",$' '"any",'
check "...primary is a host requirement"                      $J/hostchooser/HostRequirement.java '^  primary \{'
after "hostRecheckSeconds defaults to 10"                     $J/PGProperty.java '"hostRecheckSeconds",$' '"10",'
after "socketTimeout defaults to 0 (wait for ever)"           $J/PGProperty.java '"socketTimeout",$' '"0",'
after "connectTimeout defaults to 10 s"                       $J/PGProperty.java '"connectTimeout",$' '"10",'

echo "-- Toxiproxy 2.12.0"
check "a proxy can be disabled (cuts open connections)"       $SRC/toxiproxy/api.go 'server.ProxyUpdate\)'

echo
[ $fail -eq 0 ] && echo "all checks passed" || { echo "$fail checks failed"; exit 1; }
