#!/usr/bin/env bash
# Generates FoodFleet's SLO rules from chapter 51's generator with a bare JDK 25 (no Maven needed).
set -euo pipefail
unset JAVA_TOOL_OPTIONS
cd "$(dirname "$0")"
J=${JAVA_HOME:+$JAVA_HOME/bin/}
CH51=../../chapters/ch51-slo-alerting/src/main/java
rm -rf build && mkdir -p build
${J}javac -d build $(find $CH51 -name '*.java') $(find src -name '*.java')
${J}java -cp build dev.foodfleet.platform.FoodFleetSlos > foodfleet-slo.rules.yml
${J}java -cp build dev.foodfleet.platform.FoodFleetSlos --budgets > budgets.md
echo "wrote foodfleet-slo.rules.yml ($(grep -c 'alert:' foodfleet-slo.rules.yml) alerts, $(grep -c 'record:' foodfleet-slo.rules.yml) recording rules) and budgets.md"
