#!/usr/bin/env bash
# Checks the HorizontalPodAutoscaler behaviours chapter 56 relies on against Kubernetes 1.37.1's and metrics-server
# 0.9.0's sources (sparse clones made by build-libs.sh).
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
K=$SRC/k8s
C=$K/pkg/controller/podautoscaler
M=$SRC/metrics-server

echo "-- Kubernetes 1.37.1: the HPA controller"
check "syncs every 15 s by default"                                     $C/config/v1alpha1/defaults.go 'HorizontalPodAutoscalerSyncPeriod = metav1\.Duration\{Duration: 15 \* time\.Second\}'
check "scale-down stabilization window: 5 minutes"                      $C/config/v1alpha1/defaults.go 'HorizontalPodAutoscalerDownscaleStabilizationWindow = metav1\.Duration\{Duration: 5 \* time\.Minute\}'
check "tolerance: 10%"                                                  $C/config/v1alpha1/defaults.go 'HorizontalPodAutoscalerTolerance = 0\.1'
check "CPU initialization period: 5 minutes"                            $C/config/v1alpha1/defaults.go 'HorizontalPodAutoscalerCPUInitializationPeriod = metav1\.Duration\{Duration: 5 \* time\.Minute\}'
check "...samples from before a pod became ready are ignored"           $C/replica_calculator.go 'metric\.Timestamp\.Before\(condition\.LastTransitionTime\.Time\.Add\(metric\.Window\)\)'
check "unready pods count as 0% on a scale-up"                          $C/replica_calculator.go 'on a scale-up, treat unready pods as using 0% of the resource request'
check "missing pods count as 100% on a scale-down"                      $C/replica_calculator.go 'on a scale-down, treat missing pods as using 100%'
check "desired = ceil(usage ratio x ready pods)"                        $C/replica_calculator.go 'math\.Ceil\(usageRatio \* float64\(readyPodCount\)\)'
check "stabilization: highest recommendation in the window to scale down" $C/horizontal.go 'downRecommendation = max\(rec\.recommendation, downRecommendation\)'
check "default scale-up: 4 pods or 100% per 15 s"                       $K/pkg/apis/autoscaling/v2/defaults.go 'scaleUpLimitMinimumPods +int32 = 4'
check "default scale-down: up to 100% per 15 s"                         $K/pkg/apis/autoscaling/v2/defaults.go 'scaleDownLimitPercent +int32 = 100'

echo "-- metrics-server 0.9.0"
check "metric resolution: 60 s by default (k3s sets 15 s)"              $M/cmd/metrics-server/app/options/options.go 'MetricResolution: 60 \* time\.Second'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
