#!/usr/bin/env bash
# Checks the Helm 4.1.4 and kustomize 5.8.3 behaviours chapter 55 relies on against their sources (sparse clones made
# by build-libs.sh).
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
H=$SRC/helm
K=$SRC/kustomize

echo "-- Helm 4.1.4"
check "install: --server-side defaults to true"                          $H/pkg/cmd/install.go 'BoolVar\(&client\.ServerSideApply, "server-side", true'
check "upgrade: --server-side defaults to auto"                          $H/pkg/cmd/upgrade.go 'StringVar\(&client\.ServerSideApply, "server-side", "auto"'
check "...auto keeps the method the release was installed with"         $H/pkg/action/upgrade.go 'return releaseApplyMethod == "ssa", nil'
check "--force-conflicts (off by default)"                               $H/pkg/cmd/upgrade.go 'BoolVar\(&client\.ForceConflicts, "force-conflicts", false'
check "--rollback-on-failure replaces --atomic"                          $H/pkg/cmd/upgrade.go 'MarkDeprecated\("atomic", "use --rollback-on-failure instead"\)'
check "...and turns on waiting (the watcher strategy)"                   $H/pkg/action/upgrade.go 'u\.WaitStrategy = kube\.StatusWatcherStrategy'
check "...rolling back to the last successful release"                  $H/pkg/action/upgrade.go 'has been rolled back due to rollback-on-failure being set'
check "client-side updates: a three-way strategic merge patch"           $H/pkg/kube/client.go 'strategicpatch\.CreateThreeWayMergePatch\(oldData, newData, currentData'
check "the release records its apply method"                             $H/pkg/action/install.go 'ApplyMethod: +string\(determineReleaseSSApplyMethod'

echo "-- kustomize 5.8.3"
check "generators: disableNameSuffixHash"                                $K/api/types/generatoroptions.go 'DisableNameSuffixHash bool'
check "name suffix: 10 characters of a sha256 of the content"            $K/api/hasher/hasher.go 'enc := \[\]rune\(hex\[:10\]\)'
check "images: a digest replaces the tag"                                $K/api/filters/imagetag/updater.go 'case u\.ImageTag\.Digest != "":'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
