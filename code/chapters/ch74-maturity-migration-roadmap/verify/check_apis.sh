#!/usr/bin/env bash
# Checks what chapter 74 relies on against its sources (clones made by build-libs.sh): Code Maat 1.0.4's change
# coupling, git 2.56.0's log options, and the Broadleaf Commerce tag the lab reads.
# Files are read with whitespace collapsed. Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex (over the file with runs of whitespace collapsed to one space)
  if tr -s ' \t\n' '   ' < "$2" 2>/dev/null | grep -Eq -- "$3"; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
M=$SRC/code-maat/src/code_maat

echo "-- Code Maat (change coupling)"
check "degree = shared revisions / average revisions"          $M/analysis/logical_coupling.clj 'the number of shared revisions divided by the average number of revisions for the two coupled modules'
check "default minimum revisions: 5"                           $M/cmd_line.clj '"--min-revs MIN-REVS" "Minimum number of revisions to include an entity in the analysis" :default 5'
check "default minimum shared revisions: 5"                    $M/cmd_line.clj '"--min-shared-revs MIN-SHARED-REVS" "Minimum number of shared revisions to include an entity in the analysis" :default 5'
check "default minimum degree: 30%"                            $M/cmd_line.clj '"--min-coupling MIN-COUPLING" "Minimum degree of coupling \(in percentage\) to consider" :default 30'
check "change sets over 30 modules are left out"               $M/cmd_line.clj '"Maximum number of modules in a change set if it shall be included in a coupling analysis" :default 30'

echo "-- git log"
check "--no-merges: commits with one parent only"              $SRC/git/Documentation/rev-list-options.adoc '`--no-merges`:: Do not print commits with more than one parent'
check "--name-only: the changed files' names"                  $SRC/git/Documentation/diff-options.adoc '`--name-only`:: Show only the name of each changed file in the post-image tree'

echo "-- Broadleaf Commerce broadleaf-7.1.0-GA"
TMP=$(mktemp); git -C "$SRC/broadleaf" show broadleaf-7.1.0-GA:pom.xml > "$TMP" 2>/dev/null
check "modules common, core, integration, admin"               "$TMP" '<module>common</module> <module>core</module> <module>integration</module> <module>admin</module>'
rm -f "$TMP"

echo "apis: $((8 - fail)) of 8 found"
exit $fail
