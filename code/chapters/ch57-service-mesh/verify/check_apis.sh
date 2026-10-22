#!/usr/bin/env bash
# Checks the Envoy 1.39.4 and Kubernetes 1.37.1 behaviours chapter 57 relies on against their sources (sparse clones
# made by build-libs.sh).
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
E=$SRC/envoy/api/envoy
K=$SRC/k8s

echo "-- Envoy 1.39.4"
check "outlier detection: eject after 5 consecutive 5xx by default"     $E/config/cluster/v3/outlier_detection.proto 'before a consecutive 5xx ejection occurs\. Defaults to 5\.'
check "...for 30 s times the number of ejections"                       $E/config/cluster/v3/outlier_detection.proto 'Defaults to 30000ms or 30s\.'
check "...but at most 10% of the cluster by default"                    $E/config/cluster/v3/outlier_detection.proto 'can be ejected due to outlier detection\. Defaults to 10% \.'
check "...unless always_eject_one_host"                                 $E/config/cluster/v3/outlier_detection.proto 'Will eject at least one host regardless of the value if'
check "retry policy: num_retries defaults to 1"                         $E/config/route/v3/route_components.proto 'defaults to 1\. These are the same conditions documented for'
check "STRICT_DNS clusters re-resolve every 5 s by default"             $E/config/cluster/v3/cluster.proto 'value defaults to 5000ms\. For cluster types other than'
check "TLS: require_client_certificate"                                 $E/extensions/transport_sockets/tls/v3/tls.proto 'google\.protobuf\.BoolValue require_client_certificate = 2;'
check "TLS: peers matched by subject alt name (URI SAN)"                $E/extensions/transport_sockets/tls/v3/common.proto 'repeated SubjectAltNameMatcher match_typed_subject_alt_names'

echo "-- Kubernetes 1.37.1"
check "native sidecars: init containers with restartPolicy: Always"     $K/staging/src/k8s.io/api/core/v1/types.go 'RestartPolicy \*ContainerRestartPolicy `json:"restartPolicy,omitempty"'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
