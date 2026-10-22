#!/usr/bin/env bash
# Prepares chapter 63: JUnit 6 for the tests (chapter 61's build-libs.sh fetches it), and sparse clones of Pact JVM,
# Spring Cloud Contract, oasdiff and the Pact Broker for check_apis.sh. Needs git, curl and a JDK 25.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C63_SOURCES:-build/src}
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
clone https://github.com/pact-foundation/pact-jvm 4_7_5 "$SRC/pact" /core/matchers/src/main/ \
  /core/model/src/main/kotlin/au/com/dius/pact/core/model/matchingrules/
clone https://github.com/spring-cloud/spring-cloud-contract v5.0.3 "$SRC/scc" /specs/spring-cloud-contract-spec-java/src/main/java/org/springframework/cloud/contract/spec/ \
  /spring-cloud-contract-stub-runner/src/main/java/org/springframework/cloud/contract/stubrunner/
clone https://github.com/oasdiff/oasdiff v1.34.0 "$SRC/oasdiff" /checker/
clone https://github.com/pact-foundation/pact_broker v2.122.0 "$SRC/broker" /lib/pact_broker/matrix/
