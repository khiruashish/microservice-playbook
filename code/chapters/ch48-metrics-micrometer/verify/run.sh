#!/usr/bin/env bash
# Chapter 48 measurements: Micrometer 1.17.1 and its Prometheus registry, Spring Framework 7.0.9's HTTP observation
# conventions and Prometheus 3.15.0, all built or downloaded by build-libs.sh. About 9 minutes.
# Needs git, curl, python3, Linux x86-64 and a JDK 21+ (the chapter's numbers: JDK 25.0.4, two vCPUs).
#   ./run.sh                                         everything, then the configuration and API checks
#   ./run.sh aggregate decay cardinality cost gauge
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
JBIN=${JAVA_HOME:+$JAVA_HOME/bin/}
export JAVAC=${JBIN}javac
./build-libs.sh > /dev/null
SRC=${C48_SOURCES:-build/src}
LIB=build/lib/classes
SF=$SRC/sf
SPRING=$SF/spring-web/src/main/java:$SF/spring-core/src/main/java:$SRC/cl/src/main/java:$SRC/servlet/api/src/main/java
rm -rf build/main build/tools && mkdir -p build/main build/tools
# the chapter's code, checked against Spring's annotations; Spring itself isn't needed at run time
$JAVAC --release 21 -nowarn -Xlint:none -XDsuppressNotes -proc:none -implicit:none -d build/main -cp $LIB:build/lib/compile \
  -sourcepath "$SF/spring-context/src/main/java:$SF/spring-beans/src/main/java:$SPRING:build/lib/kotlin-src" \
  $(find ../src/main/java -name '*.java')
# the experiments, with the parts of spring-web and spring-core they reach compiled from source
$JAVAC --release 21 -nowarn -Xlint:none -XDsuppressNotes -proc:none -implicit:class -d build/tools -cp build/main:$LIB:build/lib/compile \
  -sourcepath "$SPRING" tools/*.java
${JBIN}java -version 2>&1 | head -1
MODES=${*:-aggregate decay cardinality cost gauge}
for m in $MODES; do
  echo "== $m"
  ${JBIN}java -Xmx3g -Dc48.bin=build/bin -cp build/main:build/tools:$LIB Measure "$m"
done

[ $# -gt 0 ] && exit 0
echo "== configs"
build/bin/promtool check config ../src/main/resources/prometheus/prometheus.yml
echo
echo "== apis"
./check_apis.sh "$SRC"
