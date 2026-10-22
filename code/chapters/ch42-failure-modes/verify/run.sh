#!/usr/bin/env bash
# Chapter 42 measurements: real HTTP services and load generators on loopback, JDK 21+ only.
#   ./run.sh                                   everything (about 12 minutes) and the API checks (needs git)
#   ./run.sh queueing amplification metastable herd tail
set -euo pipefail
cd "$(dirname "$0")"
SRC=../src/main/java
rm -rf build/main build/tools && mkdir -p build/main build/tools
javac -d build/main $(find $SRC -name '*.java' -not -path '*/web/*')
javac -d build/tools -cp build/main tools/*.java
MODES=${*:-queueing amplification metastable herd tail}
for m in $MODES; do
  echo "== $m"
  java -Xmx1g -Dstdout.encoding=UTF-8 -cp build/main:build/tools Measure "$m"
  echo
done

[ $# -gt 0 ] && exit 0
SRC_DIR=${C42_SOURCES:-build/src}
mkdir -p "$SRC_DIR"
clone() {   # url tag dir paths...
  local url=$1 tag=$2 dir=$SRC_DIR/$3; shift 3
  if [ ! -d "$dir/.git" ]; then
    git -c advice.detachedHead=false clone -q --depth 1 --branch "$tag" --filter=blob:none --sparse "$url" "$dir"
    git -C "$dir" sparse-checkout set --no-cone "$@"
  fi
}
clone https://github.com/spring-projects/spring-framework.git v7.0.9 sf \
  /spring-context/src/main/java/org/springframework/resilience/annotation/ \
  /spring-web/src/main/java/org/springframework/http/HttpHeaders.java \
  /spring-web/src/main/java/org/springframework/web/client/RestClient.java
clone https://github.com/spring-projects/spring-boot.git v4.1.1 boot \
  /module/spring-boot-tomcat/src/main/java/org/springframework/boot/tomcat/autoconfigure/ \
  /module/spring-boot-http-client/src/main/java/org/springframework/boot/http/client/autoconfigure/
echo "== apis"
./check_apis.sh "$SRC_DIR/sf" "$SRC_DIR/boot"
