#!/usr/bin/env bash
# Fetches what chapter 61's suites run on: JUnit 6.1.3 (with opentest4j, apiguardian and jspecify), the PostgreSQL
# JDBC driver 42.7.14 and H2 2.5.252, into build/lib; and sparse clones of JUnit, Spring Boot 4.1.1 and H2 for
# check_apis.sh. Chapter 62 uses the same libraries. Needs curl, git and a JDK 25.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C61_SOURCES:-build/src}
LIB=build/lib
mkdir -p "$SRC" $LIB
M=https://repo1.maven.org/maven2
fetch() {   # group path, artifact, version
  [ -f "$LIB/$2-$3.jar" ] || curl -fsSL -o "$LIB/$2-$3.jar" "$M/$1/$2/$3/$2-$3.jar"
}
for a in junit-platform-commons junit-platform-engine junit-platform-launcher; do fetch org/junit/platform $a 6.1.3; done
for a in junit-jupiter-api junit-jupiter-engine junit-jupiter-params; do fetch org/junit/jupiter $a 6.1.3; done
fetch org/opentest4j opentest4j 1.3.0
fetch org/apiguardian apiguardian-api 1.1.2
fetch org/jspecify jspecify 1.0.0
fetch org/postgresql postgresql 42.7.14
fetch com/h2database h2 2.5.252

clone() {   # repo, tag, dir, paths...
  local repo=$1 tag=$2 dir=$3; shift 3
  if [ ! -d "$dir" ]; then
    git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "$repo" "$dir" 2>/dev/null
    (cd "$dir" && git sparse-checkout set --no-cone "$@")
  fi
}
clone https://github.com/junit-team/junit-framework r6.1.3 "$SRC/junit" /gradle/plugins/common/src/main/kotlin/JavaLibraryExtension.kt \
  /junit-platform-launcher/src/main/java/org/junit/platform/launcher/ /junit-jupiter-api/src/main/java/org/junit/jupiter/api/
clone https://github.com/spring-projects/spring-boot v4.1.1 "$SRC/boot" /starter/spring-boot-starter-test/
clone https://github.com/h2database/h2database version-2.5.252 "$SRC/h2" /h2/src/main/org/h2/command/Parser.java
ls $LIB | wc -l | xargs echo "jars in $LIB:"
