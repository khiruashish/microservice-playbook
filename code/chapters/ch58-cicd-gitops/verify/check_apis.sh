#!/usr/bin/env bash
# Checks the Kubernetes 1.37.1, Gateway API 1.6.1 and Argo CD 3.5.5 behaviours chapter 58 relies on against their
# sources (sparse clones made by build-libs.sh).
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
K=$SRC/k8s/staging/src/k8s.io/kubectl/pkg/cmd
G=$SRC/gateway-api/apis/v1
A=$SRC/argo-cd

echo "-- kubectl 1.37.1"
check "diff: exit status 1 when differences were found"                 $K/diff/diff.go '^\s+Differences were found\.$'
check "diff --server-side: the API server computes the result"          $K/diff/diff.go 'ServerSideApply'
check "apply --prune requires an explicit selector or applyset"         $K/apply/apply.go 'all resources selected for prune without explicitly passing --all'

echo "-- Gateway API 1.6.1"
check "backendRefs weight: a proportion of requests"                    $G/shared_types.go 'Weight specifies the proportion of requests forwarded to the referenced'
check "...weight 0: no traffic for that backend"                        $G/shared_types.go 'If weight is set to 0, no'

echo "-- Argo CD 3.5.5"
check "reconciles every 120 s by default"                               $A/cmd/argocd-application-controller/commands/argocd_application_controller.go 'defaultAppResyncPeriod += 120'
check "automated sync doesn't prune by default"                         $A/docs/user-guide/auto_sync.md 'automated sync will not delete resources when Argo CD detects'
check "...or revert changes made in the cluster (selfHeal)"             $A/docs/user-guide/auto_sync.md 'changes that are made to the live cluster will not trigger automated sync'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
