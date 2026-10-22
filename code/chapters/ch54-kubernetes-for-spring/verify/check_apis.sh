#!/usr/bin/env bash
# Checks the Kubernetes 1.37.1 behaviours chapter 54 relies on against its source (a sparse clone made by
# build-libs.sh).
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
K=$SRC/k8s

echo "-- Kubernetes 1.37.1"
check "CPU limits are CFS quotas over a 100 ms period"                $K/pkg/kubelet/cm/helpers_linux.go 'QuotaPeriod = 100000'
check "ConfigMap volumes: files swapped in via a ..data symlink"      $K/pkg/volume/util/atomic_writer.go 'dataDirName += "\.\.data"'
check "kubelet: ConfigMap and Secret changes detected by watching"    $K/pkg/kubelet/apis/config/v1beta1/defaults.go 'ConfigMapAndSecretChangeDetectionStrategy = kubeletconfigv1beta1\.WatchChangeDetectionStrategy'
check "kubelet: pods resynced every minute by default"                $K/pkg/kubelet/apis/config/v1beta1/defaults.go 'obj\.SyncFrequency = metav1\.Duration\{Duration: 1 \* time\.Minute\}'
check "Downward API: limits.cpu / limits.memory via resourceFieldRef" $K/staging/src/k8s.io/api/core/v1/types.go 'ResourceLimitsCPU ResourceName = "limits\.cpu"'
check "...an unset limit reads as the node's allocatable"             $K/pkg/kubelet/kubelet_resources.go "to the node's allocatable resources"
check "eviction API refuses evictions that break a PodDisruptionBudget" $K/pkg/registry/core/pod/storage/eviction.go "Cannot evict pod as it would violate the pod's disruption budget\."
check "volumeMounts[].subPath"                                         $K/staging/src/k8s.io/api/core/v1/types.go 'SubPath string `json:"subPath,omitempty"'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
