#!/usr/bin/env bash
# Checks the behaviours chapter 68 relies on against their sources (sparse clones made by build-libs.sh): Kubernetes
# 1.37.1, the JDK 25.0.4 HTTP client, Apache Kafka 4.2.2, Apache Cassandra 5.0.9 and Spring Session 4.1.1.
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
K=$SRC/k8s/pkg; H=$SRC/jdk/src/java.net.http/share/classes/jdk/internal/net/http

echo "-- Kubernetes Services"
check "ClientIP affinity lasts 3 hours by default"                         $K/apis/core/types.go 'DefaultClientIPServiceAffinitySeconds int32 = 10800'
check "kube-proxy (iptables) picks an endpoint at random per connection"   $K/proxy/iptables/proxier.go '"--probability", proxier\.probability\(numEndpoints-i\)'
check "kube-proxy (nftables) the same, with numgen random"                 $K/proxy/nftables/proxier.go '"numgen random mod", len\(endpoints\)'

echo "-- JDK 25 HTTP client"
check "pooled connections stay open 30 s when idle"                        $H/HttpClientImpl.java 'DEFAULT_KEEP_ALIVE_TIMEOUT = 30;'
check "Connection is a restricted header (jdk.httpclient.allowRestrictedHeaders)" $H/common/Utils.java 'headers\.addAll\(Set\.of\("connection"'

echo "-- partitioning in the wild"
check "Kafka: a record's partition is murmur2(key) mod partitions"         $SRC/kafka/clients/src/main/java/org/apache/kafka/clients/producer/internals/BuiltInPartitioner.java 'Utils\.toPositive\(Utils\.murmur2\(serializedKey\)\) % numPartitions;'
check "Cassandra: 16 virtual nodes (tokens) per node by default"           $SRC/cassandra/conf/cassandra.yaml '^num_tokens: 16'

echo "-- sessions outside the service"
check "Spring Session JDBC stores sessions in SPRING_SESSION"              $SRC/session/spring-session-jdbc/src/main/java/org/springframework/session/jdbc/JdbcIndexedSessionRepository.java 'DEFAULT_TABLE_NAME = "SPRING_SESSION";'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
