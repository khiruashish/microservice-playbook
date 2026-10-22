#!/usr/bin/env bash
# Prepares chapter 74: JUnit 6 (chapter 61's build-libs.sh); Broadleaf Commerce's full history without file contents
# (--filter=blob:none) and its main Java sources at broadleaf-7.1.0-GA; sparse clones of Code Maat and git for
# check_apis.sh. Needs git; the Broadleaf clone is about 70 MB.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C74_SOURCES:-build/src}
mkdir -p "$SRC" build
C61_SOURCES="$(cd ../../ch61-test-strategy/verify && pwd)/build/src" ../../ch61-test-strategy/verify/build-libs.sh > /dev/null
ln -sfn "$(cd ../../ch61-test-strategy/verify/build/lib && pwd)" build/lib
if [ ! -d "$SRC/broadleaf" ]; then
  git clone -q --filter=blob:none --no-checkout https://github.com/BroadleafCommerce/BroadleafCommerce "$SRC/broadleaf"
  (cd "$SRC/broadleaf" && git sparse-checkout set --no-cone '/pom.xml' '/**/src/main/java/**' && git checkout -q broadleaf-7.1.0-GA)
fi
clone() {   # repo, tag, dir, paths...
  local repo=$1 tag=$2 dir=$3; shift 3
  if [ ! -d "$dir" ]; then
    git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "$repo" "$dir" 2>/dev/null
    (cd "$dir" && git sparse-checkout set --no-cone "$@")
  fi
}
clone https://github.com/adamtornhill/code-maat v1.0.4 "$SRC/code-maat" /src/code_maat/cmd_line.clj /src/code_maat/analysis/logical_coupling.clj
clone https://github.com/git/git v2.56.0 "$SRC/git" /Documentation/rev-list-options.adoc /Documentation/diff-options.adoc
