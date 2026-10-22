#!/usr/bin/env bash
# Fetches what the experiments and checks need, with git and curl:
#   build/bin/toxiproxy-server     Toxiproxy 2.12.0 release binary (adds "distance" to the replication link)
#   build/src/                     PostgreSQL 16.13, pgJDBC 42.7.13 (Spring Boot 4.1.1's version) and Toxiproxy sources,
#                                  sparse, for check_apis.sh
# PostgreSQL itself (initdb, pg_ctl, pg_basebackup, pg_receivewal, pgbench) must be installed: PG_BIN or pg_config.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C46_SOURCES:-build/src}
BIN=build/bin
mkdir -p "$SRC" "$BIN"

clone() {   # url tag dir sparse paths...
  local url=$1 tag=$2 dir=$SRC/$3; shift 3
  [ -d "$dir/.git" ] && return
  git -c advice.detachedHead=false clone -q --depth 1 --branch "$tag" --filter=blob:none --sparse "$url" "$dir"
  git -C "$dir" sparse-checkout set --no-cone "$@"
}
clone https://github.com/postgres/postgres.git REL_16_13 pg /src/backend/utils/misc/guc_tables.c \
  /src/backend/utils/misc/postgresql.conf.sample /src/backend/access/transam/xlogfuncs.c /doc/src/sgml/config.sgml
clone https://github.com/pgjdbc/pgjdbc.git REL42.7.13 pgjdbc /pgjdbc/src/main/java/org/postgresql/PGProperty.java \
  /pgjdbc/src/main/java/org/postgresql/hostchooser/HostRequirement.java
clone https://github.com/Shopify/toxiproxy.git v2.12.0 toxiproxy /api.go /proxy_collection.go

if [ ! -x $BIN/toxiproxy-server ]; then
  os=$(uname -s | tr '[:upper:]' '[:lower:]'); arch=$(uname -m)
  case $arch in x86_64) arch=amd64 ;; aarch64|arm64) arch=arm64 ;; esac
  curl -fsSL -o $BIN/toxiproxy-server "https://github.com/Shopify/toxiproxy/releases/download/v2.12.0/toxiproxy-server-$os-$arch"
  chmod +x $BIN/toxiproxy-server
fi
ls -1 $BIN
