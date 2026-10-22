#!/usr/bin/env bash
# Checks what chapter 72 relies on against its sources (sparse clones made by build-libs.sh): Backstage 1.55.3's
# scaffolder, Copier 9.19.1's update, git 2.56.0's merge-file, VPA 1.8.0 and Kubernetes 1.37.1.
# Files are read with whitespace collapsed. Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex (over the file with runs of whitespace collapsed to one space)
  if tr -s ' \t\n' '   ' < "$2" 2>/dev/null | grep -Eq -- "$3"; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
B=$SRC/backstage/plugins

echo "-- Backstage scaffolder"
check "a Template is scaffolder.backstage.io/v1beta3"          $B/scaffolder-common/src/Template.v1beta3.schema.json '"apiVersion": \{ "enum": \["scaffolder.backstage.io/v1beta3"\]'
check "...and needs a type and steps"                          $B/scaffolder-common/src/Template.v1beta3.schema.json '"required": \["type", "steps"\]'
check "fetch:template renders a skeleton directory"            $B/scaffolder-backend/src/scaffolder/actions/builtin/fetch/template.ts "id: 'fetch:template',"

echo "-- updating a generated project"
check "Copier update: a three-way merge with git merge-file"   $SRC/copier/copier/_main.py '"merge-file", "-L", "before updating", "-L", "last update", "-L", "after updating",'
check "git merge-file exits with the number of conflicts"      $SRC/git/Documentation/git-merge-file.adoc 'The exit value of this program is negative on error, and the number of conflicts otherwise'

echo "-- Kubernetes"
check "VPA updateMode Off: recommendations only"               $SRC/vpa/vertical-pod-autoscaler/pkg/apis/autoscaling.k8s.io/v1/types.go 'UpdateModeOff UpdateMode = "Off"'
check "server-side dry run: dryRun=All"                        $SRC/k8s/staging/src/k8s.io/apimachinery/pkg/apis/meta/v1/types.go 'DryRunAll = "All"'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
