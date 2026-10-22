#!/usr/bin/env bash
# Checks the behaviours chapter 45 relies on against the sources of Toxiproxy 2.12.0, Spring Framework 7.0.9,
# Chaos Mesh 2.8.4, LitmusChaos 3.31.0 and the JDK (its lib/src.zip, if it ships one).
# Usage: ./check_apis.sh <sources dir> [<JDK home>]
set -uo pipefail
SRC=$1 JDK=${2:-}
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
T=$SRC/toxiproxy
W=$SRC/sf/spring-web/src/main/java/org/springframework/http/client
CM=$SRC/chaosmesh
L=$SRC/litmus/deploy/crds/chaosengine_crd.yaml

echo "-- Toxiproxy 2.12.0"
check "toxic: latency (latency, jitter)"                   $T/toxics/latency.go 'Jitter  int64 `json:"jitter"`'
check "toxic: bandwidth (rate in KB/s)"                    $T/toxics/bandwidth.go 'Rate int64 `json:"rate"`'
check "toxic: timeout; 0 never closes the connection"      $T/toxics/timeout.go 'If the timeout is set to 0, then the connection will not be closed.'
check "toxic: reset_peer"                                  $T/toxics/reset_peer.go 'Register\("reset_peer"'
check "toxic: limit_data"                                  $T/toxics/limit_data.go 'Register\("limit_data"'
check "toxic: slow_close, slicer"                          $T/toxics/slicer.go 'Register\("slicer"'
check "API: POST /proxies/{proxy}/toxics"                  $T/api.go 'r.HandleFunc\("/proxies/\{proxy\}/toxics", server.ToxicCreate\)'
check "API: POST /reset (remove all toxics, enable all)"   $T/api.go 'r.HandleFunc\("/reset", server.ResetState\)'

echo "-- Spring Framework 7.0.9"
check "JdkClientHttpRequest: read timeout covers the body (JDK-8208693 workaround)" $W/JdkClientHttpRequest.java 'JDK-8208693'
check "SimpleClientHttpRequestFactory: read timeout is URLConnection's per-read timeout" $W/SimpleClientHttpRequestFactory.java 'connection.setReadTimeout\(this.readTimeout\);'

echo "-- Chaos Mesh 2.8.4"
check "NetworkChaos modes: one, all, fixed, fixed-percent, random-max-percent" $CM/manifests/crd.yaml '- random-max-percent'
check "Workflow templates: abortWithStatusCheck"           $CM/manifests/crd.yaml 'abortWithStatusCheck:'
check "StatusCheck: failureThreshold"                      $CM/manifests/crd.yaml 'failureThreshold:'
check "Schedule: concurrencyPolicy"                        $CM/manifests/crd.yaml 'concurrencyPolicy:'
check "no duration: the chaos never expires"               $CM/api/v1alpha1/zz_generated.chaosmesh.go 'func \(in \*NetworkChaos\) DurationExceeded'
check "...(DurationExceeded is false without a duration)"  $CM/api/v1alpha1/zz_generated.chaosmesh.go 'if duration != nil \{'

echo "-- LitmusChaos 3.31.0"
check "probe modes: SOT, EOT, Edge, Continuous, OnChaos"   $L '\^\(SOT\|EOT\|Edge\|Continuous\|OnChaos\)\$'
check "probe runProperties: stopOnFailure"                 $L 'stopOnFailure:'
check "promProbe inputs: endpoint, query, comparator"      $L 'promProbe/inputs:'

echo "-- JDK"
if [ -n "$JDK" ] && [ -f "$JDK/lib/src.zip" ]; then
  X=$(mktemp -d)
  unzip -q -o "$JDK/lib/src.zip" java.net.http/java/net/http/HttpRequest.java \
    java.net.http/jdk/internal/net/http/MultiExchange.java java.base/java/net/URLConnection.java -d "$X"
  check "HttpRequest.timeout: until the response (headers) is received" "$X/java.net.http/java/net/http/HttpRequest.java" 'Sets a timeout for this request. If the response is not received'
  check "HttpClient retries idempotent requests on a lost connection"   "$X/java.net.http/jdk/internal/net/http/MultiExchange.java" 'isIdempotentRequest'
  check "URLConnection read timeout: per read, not per response"        "$X/java.base/java/net/URLConnection.java" 'reading from Input stream when a connection is established'
  rm -rf "$X"
else
  echo "skipped: no lib/src.zip in '${JDK}'"
fi

echo
[ $fail -eq 0 ] && echo "all checks passed" || { echo "$fail checks failed"; exit 1; }
