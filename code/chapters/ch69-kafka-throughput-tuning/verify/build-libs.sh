#!/usr/bin/env bash
# Prepares chapter 69: JUnit 6 and the PostgreSQL driver (chapter 61's build-libs.sh), chapter 62's PostgreSQL image,
# kafkalab (Go: franz-go's client and its in-memory Kafka broker, fetched from GitHub; golang.org/x modules come from
# their GitHub mirrors), and sparse clones of Kafka, franz-go and pgjdbc for check_apis.sh. Needs docker, git, curl,
# Go 1.24+ and a JDK 25.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C69_SOURCES:-build/src}
mkdir -p "$SRC" build
../../ch62-unit-slice-integration-tests/verify/build-libs.sh > /dev/null       # chapter 61's libraries and c62/postgres:16
ln -sfn "$(cd ../../ch61-test-strategy/verify/build/lib && pwd)" build/lib
(cd kafkalab && GOPROXY=${GOPROXY:-direct} GOSUMDB=${GOSUMDB:-off} GOFLAGS=-mod=mod GOTOOLCHAIN=local go build -o ../build/kafkalab .)

clone() {   # repo, tag, dir, paths...
  local repo=$1 tag=$2 dir=$3; shift 3
  if [ ! -d "$dir" ]; then
    git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "$repo" "$dir" 2>/dev/null
    (cd "$dir" && git sparse-checkout set --no-cone "$@")
  fi
}
clone https://github.com/apache/kafka 4.2.2 "$SRC/kafka" /clients/src/main/java/org/apache/kafka/clients/producer/ProducerConfig.java \
  /clients/src/main/java/org/apache/kafka/clients/consumer/ConsumerConfig.java \
  /clients/src/main/java/org/apache/kafka/clients/producer/internals/BuiltInPartitioner.java
clone https://github.com/twmb/franz-go v1.20.6 "$SRC/franz" /pkg/kgo/config.go
clone https://github.com/pgjdbc/pgjdbc REL42.7.14 "$SRC/pgjdbc" /pgjdbc/src/main/java/org/postgresql/PGProperty.java
