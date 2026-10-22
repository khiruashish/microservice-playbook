#!/usr/bin/env bash
# Chapter 51 measurements: SLO alerting strategies evaluated by Prometheus 3.15.0's rule engine, a latency SLI from
# Micrometer 1.17.1 histograms, and the chapter's routing on Alertmanager 0.34.1. About 20 minutes (the alerting
# scenarios replay up to 30 days a minute at a time). Needs git, curl, python3, Linux x86-64 and a JDK 21+.
#   ./run.sh                          everything, then the API checks
#   ./run.sh alerts latency routing
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
JBIN=${JAVA_HOME:+$JAVA_HOME/bin/}
export JAVAC=${JBIN}javac
./build-libs.sh > /dev/null
SRC=${C51_SOURCES:-build/src}
rm -rf build/main build/tools && mkdir -p build/main build/tools
$JAVAC --release 21 -nowarn -d build/main $(find ../src/main/java -name '*.java')
$JAVAC --release 21 -nowarn -XDsuppressNotes -d build/tools -cp build/main:build/lib/micrometer tools/*.java
CP="$PWD/build/main:$PWD/build/tools:$PWD/build/lib/micrometer"
${JBIN}java -version 2>&1 | head -1
echo "== rules"
CP_MAIN="$PWD/build/main"
${JBIN}java -cp "$CP_MAIN" dev.foodfleet.ch51.rules.CheckoutSlos | diff -q - ../src/main/resources/prometheus/checkout-slo.rules.yml \
  && echo "checkout-slo.rules.yml is the generator's output"
build/bin/promtool check rules ../src/main/resources/prometheus/checkout-slo.rules.yml
build/bin/amtool check-config ../src/main/resources/alertmanager/alertmanager.yml | head -1
MODES=${*:-alerts latency routing}
for m in $MODES; do
  echo "== $m"
  ${JBIN}java -Xmx1g -Dc51.promtool="$PWD/build/bin/promtool" -Dc51.alertmanager="$PWD/build/bin/alertmanager" \
    -Dc51.amconfig="$PWD/../src/main/resources/alertmanager/alertmanager.yml" -cp "$CP" Measure "$m"
done

[ $# -gt 0 ] && exit 0
echo "== apis"
./check_apis.sh "$SRC"
