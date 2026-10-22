#!/usr/bin/env bash
# Chapter 45 measurements: real faults from Toxiproxy 2.12.0, real clients (the JDK's, Spring Framework 7.0.9's),
# Resilience4j 2.4.0, and manifest checks against the Chaos Mesh and LitmusChaos CRDs. About 8 minutes.
# Needs git, curl, a JDK (the chapter's numbers: JDK 25.0.4) and python3 with PyYAML and jsonschema.
#   ./run.sh                          everything, then the API checks
#   ./run.sh faults blast breaker manifests
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
BIN=${JAVA_HOME:+$JAVA_HOME/bin/}
export JAVAC=${BIN}javac
./build-libs.sh > /dev/null
SRC=${C45_SOURCES:-build/src}
LIBS=build/lib/resilience4j-2.4.0.jar:build/lib/spring-web-client-7.0.9.jar
rm -rf build/main build/tools && mkdir -p build/main build/tools
$JAVAC --release 21 -d build/main $(find ../src/main/java -name '*.java')
$JAVAC --release 21 -d build/tools -cp build/main:$LIBS tools/*.java
${BIN}java -version 2>&1 | head -1
MODES=${*:-faults blast breaker manifests}
for m in $MODES; do
  echo "== $m"
  if [ "$m" = manifests ]; then
    python3 -W ignore validate_manifests.py $SRC/chaosmesh/manifests/crd.yaml $SRC/litmus/deploy/chaos_crds.yaml \
      ../src/main/resources/chaos-mesh/*.yaml ../src/main/resources/litmus/*.yaml
  else
    ${BIN}java -Xmx1g -Dslf4j.internal.verbosity=ERROR -Dtoxiproxy.bin=build/bin/toxiproxy-server \
      -cp build/main:build/tools:$LIBS Measure "$m"
  fi
  echo
done

[ $# -gt 0 ] && exit 0
echo "== apis"
./check_apis.sh "$SRC" "${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
