#!/usr/bin/env bash
# Prices the generated fleet with chapter 70's Fleet (JDK only). Run deploy/generate.py first.
set -euo pipefail
unset JAVA_TOOL_OPTIONS
cd "$(dirname "$0")"
J=${JAVA_HOME:+$JAVA_HOME/bin/}
python3 fleet.py
rm -rf build && mkdir -p build
${J}javac -d build ../../chapters/ch70-finops/src/main/java/dev/foodfleet/ch70/cost/Fleet.java $(find src -name '*.java')
${J}java -cp build dev.foodfleet.platform.FleetCost fleet-min.tsv fleet-max.tsv | tee cost.md
