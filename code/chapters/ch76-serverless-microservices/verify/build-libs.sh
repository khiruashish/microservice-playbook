#!/usr/bin/env bash
# Prepares chapter 76: JUnit 6 (chapter 61's build-libs.sh) and sparse clones of Spring Cloud Function 5.0.4 and Knative
# Serving 1.23.0 for check_apis.sh. Needs git.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C76_SOURCES:-build/src}
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
F=/spring-cloud-function-context/src/main/java/org/springframework/cloud/function/context
clone https://github.com/spring-cloud/spring-cloud-function v5.0.4 "$SRC/spring-cloud-function" $F/FunctionProperties.java $F/FunctionCatalog.java \
  /spring-cloud-function-adapters/spring-cloud-function-adapter-aws/src/main/java/org/springframework/cloud/function/adapter/aws/FunctionInvoker.java
clone https://github.com/knative/serving knative-v1.23.0 "$SRC/knative" /config/core/configmaps/autoscaler.yaml
