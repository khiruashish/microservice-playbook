#!/usr/bin/env bash
# Prepares chapter 73: JUnit 6 (chapter 61's build-libs.sh) and sparse clones of adr-tools, MADR and Build Your Own
# Radar (their own decision logs are linted by Lab log; check_apis.sh reads them too). Needs git.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C73_SOURCES:-build/src}
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
clone https://github.com/npryce/adr-tools 3.0.0 "$SRC/adr-tools" /doc/adr/ /src/template.md /src/adr-new /src/adr-link
clone https://github.com/adr/madr 4.0.0 "$SRC/madr" /docs/decisions/ /template/adr-template.md
clone https://github.com/thoughtworks/build-your-own-radar v1.2.1 "$SRC/byor" /README.md /src/util/ringNormalizer.js
