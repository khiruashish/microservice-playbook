#!/usr/bin/env bash
# Prepares chapter 71: JUnit 6 (chapter 61's build-libs.sh fetches it) and sparse clones of Spectral's rulesets,
# Zalando's RESTful API guidelines, Backstage's catalog model and Spring Framework's web module for check_apis.sh.
# Needs git, curl and a JDK 25.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C71_SOURCES:-build/src}
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
clone https://github.com/stoplightio/spectral @stoplight/spectral-rulesets-v1.16.0 "$SRC/spectral" /packages/rulesets/src/oas/index.ts
clone https://github.com/backstage/backstage v1.55.3 "$SRC/backstage" /packages/catalog-model/src/schema/kinds/API.v1alpha1.schema.json \
  /packages/catalog-model/src/schema/kinds/Component.v1alpha1.schema.json
clone https://github.com/spring-projects/spring-framework v7.0.9 "$SRC/spring" \
  /spring-web/src/main/java/org/springframework/web/accept/StandardApiVersionDeprecationHandler.java
if [ ! -d "$SRC/zalando" ]; then   # the guidelines have no release tags: a fixed commit
  git init -q "$SRC/zalando"
  (cd "$SRC/zalando" && git remote add origin https://github.com/zalando/restful-api-guidelines && git fetch -q --depth 1 origin 19a1905ad50ad71968c473009b157fd9245f5c66 \
    && git checkout -q FETCH_HEAD)
fi
