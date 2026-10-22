#!/usr/bin/env bash
# Fetches and builds what the experiments run against, from release tags, with git, curl and a JDK:
#   build/bin/toxiproxy-server                    Toxiproxy 2.12.0 release binary for this OS and CPU
#   build/lib/resilience4j-2.4.0.jar              Resilience4j core and circuit breaker (+ slf4j-api, JSR-305)
#   build/lib/spring-web-client-7.0.9.jar         Spring Framework 7.0.9's client request factories, with the parts of
#                                                 spring-core they use, commons-logging 1.3.6 and JSpecify 1.0.0
# and keeps the sources (plus the Chaos Mesh 2.8.4 and LitmusChaos 3.31.0 CRDs) in build/src for the checks.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C45_SOURCES:-build/src}
LIB=build/lib
BIN=build/bin
JAVAC=${JAVAC:-javac}
mkdir -p "$SRC" "$LIB" "$BIN"
unset JAVA_TOOL_OPTIONS

clone() {   # url tag dir [sparse paths...]
  local url=$1 tag=$2 dir=$SRC/$3; shift 3
  [ -d "$dir/.git" ] && return
  git -c advice.detachedHead=false clone -q --depth 1 --branch "$tag" --filter=blob:none --sparse "$url" "$dir"
  git -C "$dir" sparse-checkout set --no-cone "$@"
}
clone https://github.com/Shopify/toxiproxy.git v2.12.0 toxiproxy /toxics/ /api.go
clone https://github.com/resilience4j/resilience4j.git v2.4.0 r4j /resilience4j-core/src/main/java/ /resilience4j-circuitbreaker/src/main/java/
clone https://github.com/qos-ch/slf4j.git v_2.0.17 slf4j /slf4j-api/src/main/java/
clone https://github.com/amaembo/jsr-305.git master jsr305 /ri/src/main/java/
clone https://github.com/spring-projects/spring-framework.git v7.0.9 sf /spring-core/src/main/java/ /spring-web/src/main/java/
clone https://github.com/apache/commons-logging.git rel/commons-logging-1.3.6 cl /src/main/java/
clone https://github.com/jspecify/jspecify.git v1.0.0 jspecify /src/main/java/
clone https://github.com/chaos-mesh/chaos-mesh.git v2.8.4 chaosmesh /manifests/crd.yaml /api/v1alpha1/
clone https://github.com/litmuschaos/chaos-operator.git 3.31.0 litmus /deploy/

if [ ! -x $BIN/toxiproxy-server ]; then
  os=$(uname -s | tr '[:upper:]' '[:lower:]'); arch=$(uname -m)
  case $arch in x86_64) arch=amd64 ;; aarch64|arm64) arch=arm64 ;; esac
  curl -fsSL -o $BIN/toxiproxy-server "https://github.com/Shopify/toxiproxy/releases/download/v2.12.0/toxiproxy-server-$os-$arch"
  chmod +x $BIN/toxiproxy-server
fi

if [ ! -f $LIB/resilience4j-2.4.0.jar ]; then
  rm -rf build/cls-r4j && mkdir -p build/cls-r4j
  $JAVAC --release 21 -nowarn -proc:none -d build/cls-r4j $(find $SRC/jsr305/ri/src/main/java -name '*.java') \
    $(find $SRC/slf4j/slf4j-api/src/main/java -name '*.java' -not -name module-info.java) \
    $(find $SRC/r4j/resilience4j-core/src/main/java $SRC/r4j/resilience4j-circuitbreaker/src/main/java -name '*.java' -not -name module-info.java) \
    > build/javac-r4j.log 2>&1 || { cat build/javac-r4j.log; exit 1; }
  jar cf $LIB/resilience4j-2.4.0.jar -C build/cls-r4j .
fi

if [ ! -f $LIB/spring-web-client-7.0.9.jar ]; then
  rm -rf build/cls-sf && mkdir -p build/cls-sf
  web=$SRC/sf/spring-web/src/main/java/org/springframework/http/client
  $JAVAC --release 21 -nowarn -proc:none -implicit:class -d build/cls-sf \
    -sourcepath "$SRC/sf/spring-web/src/main/java:$SRC/sf/spring-core/src/main/java:$SRC/cl/src/main/java:$SRC/jspecify/src/main/java" \
    $web/JdkClientHttpRequestFactory.java $web/SimpleClientHttpRequestFactory.java \
    $SRC/cl/src/main/java/org/apache/commons/logging/impl/{LogFactoryImpl,SimpleLog,Jdk14Logger,NoOpLog,WeakHashtable}.java \
    > build/javac-sf.log 2>&1 || { cat build/javac-sf.log; exit 1; }
  jar cf $LIB/spring-web-client-7.0.9.jar -C build/cls-sf .
fi
ls -1 $LIB $BIN
