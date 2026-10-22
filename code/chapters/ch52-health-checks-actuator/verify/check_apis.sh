#!/usr/bin/env bash
# Checks the defaults and behaviours chapter 52 relies on against the sources of Spring Boot 4.1.1 and Kubernetes
# 1.37.1 (sparse clones made by build-libs.sh).
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
H=$SRC/boot/module/spring-boot-health/src/main/java/org/springframework/boot/health
A=$SRC/boot/module/spring-boot-actuator/src/main/java/org/springframework/boot/actuate
AA=$SRC/boot/module/spring-boot-actuator-autoconfigure/src/main/java/org/springframework/boot/actuate/autoconfigure
K=$SRC/k8s

echo "-- Spring Boot 4.1.1"
check "health statuses ranked DOWN, OUT_OF_SERVICE, UP, UNKNOWN"     $H/contributor/Status.java 'DEFAULT_ORDER = List\.of\(DOWN, OUT_OF_SERVICE, UP, UNKNOWN\);'
check "DOWN and OUT_OF_SERVICE answer HTTP 503"                      $H/actuate/endpoint/SimpleHttpCodeStatusMapper.java 'Status\.OUT_OF_SERVICE\.getCode\(\), WebEndpointResponse\.STATUS_SERVICE_UNAVAILABLE'
check "probe groups: liveness = livenessState, readiness = readinessState" $H/autoconfigure/actuate/endpoint/AvailabilityProbesHealthEndpointGroups.java 'READINESS, "/readyz", "readinessState"'
check "probes on by default (not only on Kubernetes)"               $H/autoconfigure/actuate/endpoint/AvailabilityProbesAutoConfiguration.java 'name = "management\.endpoint\.health\.probes\.enabled", matchIfMissing = true'
check "readiness REFUSING_TRAFFIC is OUT_OF_SERVICE"                 $H/application/ReadinessStateHealthIndicator.java 'statusMappings\.add\(ReadinessState\.REFUSING_TRAFFIC, Status\.OUT_OF_SERVICE\);'
check "liveness BROKEN is DOWN"                                      $H/application/LivenessStateHealthIndicator.java 'statusMappings\.add\(LivenessState\.BROKEN, Status\.DOWN\);'
check "health show-details defaults to never"                       $H/autoconfigure/actuate/endpoint/HealthEndpointProperties.java 'private Show showDetails = Show\.NEVER;'
check "web exposure includes only health by default"                 $AA/endpoint/expose/EndpointExposure.java 'WEB\("health"\);'
check "management.endpoints.access.default"                          $AA/endpoint/PropertiesEndpointAccessResolver.java 'DEFAULT_ACCESS_KEY = "management\.endpoints\.access\.default";'
check "heapdump endpoint: no access unless granted"                  $A/management/HeapDumpWebEndpoint.java '@WebEndpoint\(id = "heapdump", defaultAccess = Access\.NONE\)'
check "env endpoint: values never shown by default"                  $AA/env/EnvironmentEndpointProperties.java 'private Show showValues = Show\.NEVER;'
check "management.server.port (a separate port)"                     $AA/web/server/ManagementServerProperties.java 'private @Nullable Integer port;'
check "server.shutdown defaults to graceful"                         $SRC/boot/module/spring-boot-web-server/src/main/java/org/springframework/boot/web/server/autoconfigure/ServerProperties.java 'private Shutdown shutdown = Shutdown\.GRACEFUL;'

echo "-- Kubernetes 1.37.1"
check "probe defaults: timeoutSeconds 1"                             $K/pkg/apis/core/v1/defaults.go 'obj\.TimeoutSeconds = 1'
check "...periodSeconds 10"                                          $K/pkg/apis/core/v1/defaults.go 'obj\.PeriodSeconds = 10'
check "...failureThreshold 3"                                        $K/pkg/apis/core/v1/defaults.go 'obj\.FailureThreshold = 3'
check "HTTP probes succeed on 200-399"                               $K/pkg/probe/http/http.go 'res\.StatusCode >= http\.StatusOK && res\.StatusCode < http\.StatusBadRequest'
check "terminationGracePeriodSeconds defaults to 30"                 $K/staging/src/k8s.io/api/core/v1/types.go 'DefaultTerminationGracePeriodSeconds = 30'
check "preStop sleep action (no shell needed in the image)"          $K/staging/src/k8s.io/api/core/v1/types.go 'Sleep \*SleepAction `json:"sleep,omitempty"'
check "liveness and readiness probes wait for the startup probe"     $K/pkg/kubelet/prober/worker.go '// Disable other probes until container has started\.'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
