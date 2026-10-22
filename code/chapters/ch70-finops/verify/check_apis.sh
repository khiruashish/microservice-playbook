#!/usr/bin/env bash
# Checks the rules and defaults chapter 70 relies on against their sources (sparse clones made by build-libs.sh): the
# Vertical Pod Autoscaler 1.8.0 and OpenCost 2.5.3. Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
V=$SRC/vpa/vertical-pod-autoscaler/pkg/recommender
O=$SRC/opencost

echo "-- VPA recommender defaults"
check "CPU target: the 90th percentile of usage"                     $V/config/config.go 'TargetCPUPercentile: +0\.9,'
check "lower and upper bounds: 50th and 95th percentiles"            $V/config/config.go 'LowerBoundCPUPercentile: +0\.5,'
check "...upper bound 95th"                                          $V/config/config.go 'UpperBoundCPUPercentile: +0\.95,'
check "memory target: the 90th percentile (of interval peaks)"       $V/config/config.go 'TargetMemoryPercentile: +0\.9,'
check "a 15% safety margin on top"                                   $V/config/config.go 'SafetyMarginFraction: +0\.15,'
check "at least 25 millicores per pod..."                            $V/config/config.go 'PodMinCPUMillicores: +25,'
check "...and 250 MiB"                                               $V/config/config.go 'PodMinMemoryMb: +250,'
check "memory samples are per-interval peaks"                        $V/config/config.go 'one memory usage sample per interval \(the maximum usage over that interval\)'
check "the margin is applied to target and bounds"                   $V/logic/recommender.go 'targetCPU = WithCPUMargin\(config\.SafetyMarginFraction, targetCPU\)'

echo "-- OpenCost 2.5.3"
check "a container is charged its request when it uses less"         $O/pkg/costmodel/allocation_helpers.go 'If CPU allocation is less than requests, set CPUCoreHours to'
check "default price: \$0.031611 per vCPU-hour"                      $O/configs/default.json '"CPU": "0\.031611"'
check "...\$0.004237 per GiB-hour"                                   $O/configs/default.json '"RAM": "0\.004237"'
check "...spot: \$0.006655 and \$0.000892"                            $O/configs/default.json '"spotCPU": "0\.006655"'
check "...based on GCP us-central1"                                  $O/configs/default.json 'Default prices based on GCP us-central1'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
