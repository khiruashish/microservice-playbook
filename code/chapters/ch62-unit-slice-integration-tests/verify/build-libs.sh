#!/usr/bin/env bash
# Prepares chapter 62's experiments: the libraries chapter 61 fetches (JUnit 6.1.3, the PostgreSQL driver, H2), a
# PostgreSQL 16 image, and sparse clones of Spring Boot 4.1.1, Spring Framework 7.0.9, Testcontainers 2.0.5, JUnit
# and H2 for check_apis.sh. Needs docker, git and a JDK 25.
#   The image: with PostgreSQL 16 installed on the host (/usr/lib/postgresql/16), c62/postgres:16 is built from its
#   binaries (no base image); it initialises a data directory on first start, as the official image does. Without
#   it, set C62_IMAGE=postgres:16 for run.sh.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C62_SOURCES:-build/src}
mkdir -p "$SRC" build
C61_SOURCES="$(cd ../../ch61-test-strategy/verify && pwd)/build/src" ../../ch61-test-strategy/verify/build-libs.sh > /dev/null
ln -sfn "$(cd ../../ch61-test-strategy/verify/build/lib && pwd)" build/lib

P=/usr/lib/postgresql/16
if [ -x $P/bin/postgres ] && ! docker image inspect c62/postgres:16 > /dev/null 2>&1; then
  R=build/pg/rootfs; rm -rf build/pg && mkdir -p $R/{etc,tmp,bin,usr/share/zoneinfo/Etc,usr/share/postgresql,usr/lib/postgresql/16}
  cp -a $P/bin $P/lib $R/usr/lib/postgresql/16/ && rm -rf $R/usr/lib/postgresql/16/lib/bitcode $R/usr/lib/postgresql/16/lib/llvmjit*
  cp -a /usr/share/postgresql/16 $R/usr/share/postgresql/
  cp /usr/share/zoneinfo/UTC $R/usr/share/zoneinfo/UTC && cp /usr/share/zoneinfo/UTC $R/usr/share/zoneinfo/Etc/UTC
  cp /bin/dash $R/bin/sh
  for f in $(for b in $P/bin/postgres $P/bin/initdb $P/bin/pg_ctl /bin/dash $P/lib/dict_snowball.so $P/lib/plpgsql.so; do ldd $b; done \
      | awk '/=>/{print $3} /ld-linux/{print $1}' | sort -u); do mkdir -p "$R$(dirname "$f")" && cp -L "$f" "$R$f"; done
  printf 'root:x:0:0::/:/bin/sh\npostgres:x:999:999::/tmp:/bin/sh\n' > $R/etc/passwd
  printf 'root:x:0:\npostgres:x:999:\n' > $R/etc/group
  cp postgres-image/entrypoint.sh $R/entrypoint.sh && chmod 755 $R/entrypoint.sh && chmod 1777 $R/tmp
  cp postgres-image/Dockerfile build/pg/Dockerfile
  docker build -q -t c62/postgres:16 build/pg > /dev/null
fi

clone() {   # repo, tag, dir, paths...
  local repo=$1 tag=$2 dir=$3; shift 3
  if [ ! -d "$dir" ]; then
    git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "$repo" "$dir" 2>/dev/null
    (cd "$dir" && git sparse-checkout set --no-cone "$@")
  fi
}
clone https://github.com/spring-projects/spring-boot v4.1.1 "$SRC/boot" /module/spring-boot-jdbc-test/src/main/ \
  /module/spring-boot-data-jpa-test/src/main/ /module/spring-boot-webmvc-test/src/main/ /module/spring-boot-jdbc/src/main/ \
  /core/spring-boot-testcontainers/src/main/
clone https://github.com/spring-projects/spring-framework v7.0.9 "$SRC/sf" /spring-test/src/main/java/org/springframework/test/context/cache/
clone https://github.com/testcontainers/testcontainers-java 2.0.5 "$SRC/tc" /modules/postgresql/src/main/ /modules/junit-jupiter/src/main/ \
  /core/src/main/java/org/testcontainers/utility/ /core/src/main/java/org/testcontainers/containers/GenericContainer.java
clone https://github.com/junit-team/junit-framework r6.1.3 "$SRC/junit" /junit-jupiter-api/src/main/java/org/junit/jupiter/api/extension/
clone https://github.com/h2database/h2database version-2.5.252 "$SRC/h2" /h2/src/main/org/h2/command/Parser.java
