#!/usr/bin/env bash
# Checks the settings and defaults chapter 69 relies on against their sources (sparse clones made by build-libs.sh):
# the Apache Kafka 4.2.2 Java client, franz-go 1.20.6 (the client kafkalab measures with) and pgjdbc 42.7.14.
# Files are read with whitespace collapsed, so a definition spread over several lines matches as one.
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex (over the file with runs of whitespace collapsed to one space)
  if tr -s ' \t\n' '   ' < "$2" 2>/dev/null | grep -Eq -- "$3"; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
P=$SRC/kafka/clients/src/main/java/org/apache/kafka/clients/producer/ProducerConfig.java
C=$SRC/kafka/clients/src/main/java/org/apache/kafka/clients/consumer/ConsumerConfig.java
G=$SRC/franz/pkg/kgo/config.go

echo "-- Kafka 4.2.2 producer defaults"
check "linger.ms = 5 (0 before Kafka 4.0, KIP-1030)"           $P 'define\(LINGER_MS_CONFIG, Type\.LONG, 5,'
check "batch.size = 16384 bytes per partition"                 $P 'define\(BATCH_SIZE_CONFIG, Type\.INT, 16384,'
check "acks = all"                                             $P 'define\(ACKS_CONFIG, Type\.STRING, "all",'
check "enable.idempotence = true"                              $P 'define\(ENABLE_IDEMPOTENCE_CONFIG, Type\.BOOLEAN, true,'
check "compression.type = none"                                $P 'define\(COMPRESSION_TYPE_CONFIG, Type\.STRING, CompressionType\.NONE\.name,'
check "max.in.flight.requests.per.connection = 5"              $P 'define\(MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, Type\.INT, 5,'
check "a keyed record's partition: murmur2(key) mod partitions" $SRC/kafka/clients/src/main/java/org/apache/kafka/clients/producer/internals/BuiltInPartitioner.java 'Utils\.toPositive\(Utils\.murmur2\(serializedKey\)\) % numPartitions;'

echo "-- Kafka 4.2.2 consumer defaults"
check "max.poll.records = 500"                                 $C 'DEFAULT_MAX_POLL_RECORDS = 500;'
check "fetch.min.bytes = 1"                                    $C 'DEFAULT_FETCH_MIN_BYTES = 1;'
check "fetch.max.wait.ms = 500"                                $C 'DEFAULT_FETCH_MAX_WAIT_MS = 500;'
check "enable.auto.commit = true, every 5000 ms"               $C 'define\(ENABLE_AUTO_COMMIT_CONFIG, Type\.BOOLEAN, true, Importance\.MEDIUM, ENABLE_AUTO_COMMIT_DOC\) \.define\(AUTO_COMMIT_INTERVAL_MS_CONFIG, Type\.INT, 5000,'
check "max.poll.interval.ms = 300000"                          $C 'define\(MAX_POLL_INTERVAL_MS_CONFIG, Type\.INT, 300000,'

echo "-- franz-go 1.20.6 defaults (kafkalab sets every one of these explicitly)"
check "linger 10 ms"                                           $G 'linger: 10 \* time\.Millisecond,'
check "1 produce request in flight without idempotence (5 with it)" $G 'maxProduceInflight: 1,'
check "snappy compression"                                     $G 'compression: \[\]CompressionCodec\{SnappyCompression\(\), NoCompression\(\)\},'

echo "-- pgjdbc 42.7.14"
check "reWriteBatchedInserts is off by default"                $SRC/pgjdbc/pgjdbc/src/main/java/org/postgresql/PGProperty.java 'REWRITE_BATCHED_INSERTS\( "reWriteBatchedInserts", "false",'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
