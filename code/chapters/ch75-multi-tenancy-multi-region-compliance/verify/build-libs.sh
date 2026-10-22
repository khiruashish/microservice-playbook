#!/usr/bin/env bash
# Prepares chapter 75: JUnit 6, the PostgreSQL JDBC driver and H2 (chapter 61's build-libs.sh), and a sparse clone of
# PostgreSQL 16.13's documentation for check_apis.sh. Needs git; the experiments need PostgreSQL 16 server binaries.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C75_SOURCES:-build/src}
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
clone https://github.com/postgres/postgres REL_16_13 "$SRC/postgres" /doc/src/sgml/ddl.sgml /doc/src/sgml/ref/set.sgml /doc/src/sgml/func.sgml \
  /doc/src/sgml/ref/vacuum.sgml /doc/src/sgml/ref/alter_table.sgml
