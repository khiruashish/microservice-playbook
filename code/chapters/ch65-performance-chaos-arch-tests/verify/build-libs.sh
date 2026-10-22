#!/usr/bin/env bash
# Prepares chapter 65: JUnit 6 (chapter 61's build-libs.sh fetches it) and sparse clones of k6, Gatling, HdrHistogram,
# Toxiproxy and ArchUnit for check_apis.sh. Needs git, curl and a JDK 25.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C65_SOURCES:-build/src}
mkdir -p "$SRC" build
C61_SOURCES="$(cd ../../ch61-test-strategy/verify && pwd)/build/src" ../../ch61-test-strategy/verify/build-libs.sh > /dev/null
ln -sfn "$(cd ../../ch61-test-strategy/verify/build/lib && pwd)" build/lib
clone() {   # repo, tag, dir, paths...
  local repo=$1 tag=$2 dir=$3; shift 3
  if [ ! -d "$dir" ]; then
    git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "$repo" "$dir" 2>/dev/null
    (cd "$dir" && git sparse-checkout set --no-cone "$@")
  fi
}
clone https://github.com/grafana/k6 v2.3.0 "$SRC/k6" /lib/executor/
clone https://github.com/gatling/gatling v3.16.0 "$SRC/gatling" /gatling-core/src/main/scala/io/gatling/core/controller/inject/
clone https://github.com/HdrHistogram/HdrHistogram HdrHistogram-2.2.2 "$SRC/hdr" /src/main/java/org/HdrHistogram/AbstractHistogram.java
clone https://github.com/Shopify/toxiproxy v2.13.0 "$SRC/toxiproxy" /toxics/
clone https://github.com/TNG/ArchUnit v1.5.1 "$SRC/archunit" /archunit/src/main/java/com/tngtech/archunit/library/ \
  /archunit/src/main/java/com/tngtech/archunit/core/importer/JavaClassProcessor.java
