#!/usr/bin/env bash
# Builds the libraries the experiments run against, from their release tags, with nothing but git and a JDK:
#   build/lib/concurrency-limits-core-0.5.4.jar   Netflix concurrency-limits (limits, limiters), with slf4j-api 2.0.17
#   build/lib/resilience4j-2.4.0.jar               Resilience4j core and hedge, with JSR-305
# (In the application, Maven brings these in; see ../pom.xml.) Sources stay in build/src for check_apis.sh.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C44_SOURCES:-build/src}
LIB=build/lib
JAVAC=${JAVAC:-javac}
mkdir -p "$SRC" "$LIB"
unset JAVA_TOOL_OPTIONS

clone() {   # url tag dir [sparse paths...]
  local url=$1 tag=$2 dir=$SRC/$3; shift 3
  [ -d "$dir/.git" ] && return
  git -c advice.detachedHead=false clone -q --depth 1 --branch "$tag" --filter=blob:none --sparse "$url" "$dir"
  git -C "$dir" sparse-checkout set "$@"
}
clone https://github.com/Netflix/concurrency-limits.git v0.5.4 ncl concurrency-limits-core/src/main/java
clone https://github.com/resilience4j/resilience4j.git v2.4.0 r4j resilience4j-core/src/main/java resilience4j-hedge/src/main/java
clone https://github.com/qos-ch/slf4j.git v_2.0.17 slf4j slf4j-api/src/main/java
clone https://github.com/amaembo/jsr-305.git master jsr305 ri/src/main/java

if [ ! -f $LIB/concurrency-limits-core-0.5.4.jar ] || [ ! -f $LIB/resilience4j-2.4.0.jar ]; then
  rm -rf build/cls-dep build/cls-ncl build/cls-r4j && mkdir -p build/cls-dep build/cls-ncl build/cls-r4j
  $JAVAC --release 21 -nowarn -proc:none -d build/cls-dep $(find $SRC/jsr305/ri/src/main/java -name '*.java') \
    $(find $SRC/slf4j/slf4j-api/src/main/java -name '*.java' -not -name module-info.java) > build/javac-dep.log 2>&1 || { cat build/javac-dep.log; exit 1; }
  $JAVAC --release 21 -nowarn -proc:none -d build/cls-ncl -cp build/cls-dep $(find $SRC/ncl/concurrency-limits-core/src/main/java -name '*.java') \
    > build/javac-ncl.log 2>&1 || { cat build/javac-ncl.log; exit 1; }
  $JAVAC --release 21 -nowarn -proc:none -d build/cls-r4j -cp build/cls-dep $(find $SRC/r4j/resilience4j-core/src/main/java $SRC/r4j/resilience4j-hedge/src/main/java -name '*.java' -not -name module-info.java) \
    > build/javac-r4j.log 2>&1 || { cat build/javac-r4j.log; exit 1; }
  cp -r build/cls-dep/. build/cls-ncl/
  jar cf $LIB/concurrency-limits-core-0.5.4.jar -C build/cls-ncl .
  jar cf $LIB/resilience4j-2.4.0.jar -C build/cls-r4j .
fi
ls -1 $LIB
