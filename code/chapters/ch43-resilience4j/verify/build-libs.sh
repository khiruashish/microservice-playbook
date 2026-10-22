#!/usr/bin/env bash
# Builds the libraries the experiments run against, from their release tags, with nothing but git and a JDK:
#   build/lib/resilience4j-2.4.0.jar        core, circuitbreaker, retry, ratelimiter, bulkhead, timelimiter, hedge,
#                                           circularbuffer and annotations, with slf4j-api 2.0.17 and JSR-305
#   build/lib/spring-retry-7.0.9.jar        Spring Framework 7.0.9's org.springframework.core.retry, util.backoff and
#                                           ConcurrencyThrottleSupport (what @Retryable and @ConcurrencyLimit run on),
#                                           with commons-logging 1.3.6 and JSpecify 1.0.0
# (In the application itself, Maven brings these in; see ../pom.xml.) The sources stay in build/src for check_apis.sh.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C43_SOURCES:-build/src}
LIB=build/lib
mkdir -p "$SRC" "$LIB"
unset JAVA_TOOL_OPTIONS

clone() {   # url tag dir [sparse paths...]
  local url=$1 tag=$2 dir=$SRC/$3; shift 3
  [ -d "$dir/.git" ] && return
  if [ $# -gt 0 ]; then
    git -c advice.detachedHead=false clone -q --depth 1 --branch "$tag" --filter=blob:none --sparse "$url" "$dir"
    git -C "$dir" sparse-checkout set "$@"
  else
    git -c advice.detachedHead=false clone -q --depth 1 --branch "$tag" "$url" "$dir"
  fi
}
clone https://github.com/resilience4j/resilience4j.git v2.4.0 r4j
clone https://github.com/qos-ch/slf4j.git v_2.0.17 slf4j slf4j-api/src/main/java
clone https://github.com/amaembo/jsr-305.git master jsr305 ri/src/main/java
clone https://github.com/spring-projects/spring-framework.git v7.0.9 sf \
  spring-core/src/main/java spring-context/src/main/java/org/springframework/resilience
clone https://github.com/apache/commons-logging.git rel/commons-logging-1.3.6 cl src/main/java
clone https://github.com/jspecify/jspecify.git v1.0.0 jspecify src/main/java

if [ ! -f $LIB/resilience4j-2.4.0.jar ]; then
  rm -rf build/cls-r4j && mkdir -p build/cls-r4j
  javac -nowarn -proc:none -d build/cls-r4j $(find $SRC/jsr305/ri/src/main/java -name '*.java') \
    $(find $SRC/slf4j/slf4j-api/src/main/java -name '*.java' -not -name module-info.java)
  files=()
  for m in core circularbuffer circuitbreaker retry ratelimiter bulkhead timelimiter hedge annotations; do
    while IFS= read -r f; do files+=("$f"); done < <(find $SRC/r4j/resilience4j-$m/src/main/java -name '*.java' -not -name module-info.java)
  done
  javac -nowarn -proc:none -d build/cls-r4j -cp build/cls-r4j "${files[@]}" > build/javac-r4j.log 2>&1 || { cat build/javac-r4j.log; exit 1; }
  jar cf $LIB/resilience4j-2.4.0.jar -C build/cls-r4j .
fi

if [ ! -f $LIB/spring-retry-7.0.9.jar ]; then
  rm -rf build/cls-sf && mkdir -p build/cls-sf
  core=$SRC/sf/spring-core/src/main/java/org/springframework
  javac -nowarn -proc:none -implicit:class -d build/cls-sf -cp build/cls-r4j \
    -sourcepath "$SRC/sf/spring-core/src/main/java:$SRC/cl/src/main/java:$SRC/jspecify/src/main/java" \
    $core/core/retry/*.java $core/core/retry/support/*.java $core/util/ConcurrencyThrottleSupport.java $core/util/backoff/*.java \
    $SRC/cl/src/main/java/org/apache/commons/logging/impl/{LogFactoryImpl,SimpleLog,Jdk14Logger,NoOpLog,WeakHashtable,Slf4jLogFactory}.java \
    > build/javac-sf.log 2>&1 || { cat build/javac-sf.log; exit 1; }
  jar cf $LIB/spring-retry-7.0.9.jar -C build/cls-sf .
fi
ls -1 $LIB
