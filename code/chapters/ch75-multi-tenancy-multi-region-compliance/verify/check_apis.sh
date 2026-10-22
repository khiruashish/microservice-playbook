#!/usr/bin/env bash
# Checks what chapter 75 relies on against PostgreSQL 16.13's documentation (a sparse clone made by build-libs.sh).
# Files are read with whitespace collapsed. Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex (over the file with runs of whitespace collapsed to one space)
  if tr -s ' \t\n' '   ' < "$2" 2>/dev/null | grep -Eq -- "$3"; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
D=$SRC/postgres/doc/src/sgml

echo "-- row-level security"
check "superusers and BYPASSRLS roles always bypass RLS"       $D/ddl.sgml 'Superusers and roles with the <literal>BYPASSRLS</literal> attribute always bypass the row security system when accessing a table'
check "owners bypass RLS unless FORCE ROW LEVEL SECURITY"      $D/ddl.sgml 'Table owners normally bypass row security as well, though a table owner can choose to be subject to row security with <link linkend="sql-altertable">ALTER TABLE \.\.\. FORCE ROW LEVEL SECURITY'
check "RLS with no policy: default deny"                       $D/ddl.sgml 'If no policy exists for the table, a default-deny policy is used, meaning that no rows are visible or can be modified'

echo "-- the tenant setting"
check "SET LOCAL lasts until COMMIT or ROLLBACK"               $D/ref/set.sgml 'Specifies that the command takes effect for only the current transaction\. After <command>COMMIT</command> or <command>ROLLBACK</command>'
check "current_setting(name, true) returns NULL when missing"  $D/func.sgml '<function>current_setting</function> throws an error unless <parameter>missing_ok</parameter> is supplied and is <literal>true</literal> \(in which case NULL is returned\)'

echo "-- storage"
check "VACUUM FULL writes a new file"                          $D/ref/vacuum.sgml '<command>VACUUM FULL</command> rewrites the entire contents of the table into a new disk file'
check "ADD COLUMN with a non-volatile default: no rewrite"     $D/ref/alter_table.sgml 'non-volatile <literal>DEFAULT</literal> is specified, the default is evaluated at the time of the statement and the result stored in the table.s metadata\. .* In neither case is a rewrite of the table required'

echo "apis: $((7 - fail)) of 7 found"
exit $fail
