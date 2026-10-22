#!/usr/bin/env bash
# Lab 19's measured part, plain Java (no Maven, no Docker), about 40 seconds:
#   contracts  chapter 63: 12 changes to menu-service against an OpenAPI diff, two consumers' contracts and E2E
#   arch       chapter 65: 10 planted violations against FoodFleet's 5 architecture rules, read from bytecode as
#              ArchUnit reads it; a frozen baseline for legacy code
set -euo pipefail
unset JAVA_TOOL_OPTIONS
cd "$(dirname "$0")"
J=${JAVA_HOME:+$JAVA_HOME/bin/}
C=../../../chapters
W=build; rm -rf $W && mkdir -p $W/{m63,t63,w63,m65,t65,w65}
MODES=${*:-contracts arch}

if [[ " $MODES " == *" contracts "* ]]; then
  ${J}javac --release 25 -nowarn -d $W/m63 $(find $C/ch63-contract-testing/src/main/java -name '*.java')
  ${J}javac --release 25 -nowarn -d $W/t63 -cp $W/m63 $C/ch63-contract-testing/verify/tools/Lab.java
  (cd $C/ch63-contract-testing/verify && ${J}java -cp "$OLDPWD/$W/m63:$OLDPWD/$W/t63" Lab ../src/main/java \
     ../src/main/resources/openapi/menu-service.json ../src/test/resources/contracts changes.tsv "$OLDPWD/$W/w63" data-changes.tsv)
fi
if [[ " $MODES " == *" arch "* ]]; then
  ${J}javac --release 25 -nowarn -d $W/m65 $(find $C/ch65-performance-chaos-arch-tests/src/main/java -name '*.java')
  ${J}javac --release 25 -nowarn -d $W/t65 -cp $W/m65 $C/ch65-performance-chaos-arch-tests/verify/tools/Lab.java
  (cd $C/ch65-performance-chaos-arch-tests/verify && ${J}java -cp "$OLDPWD/$W/m65:$OLDPWD/$W/t65" Lab \
     ../src/main/java/dev/foodfleet/ch65/sample violations.tsv "$OLDPWD/$W/w65" arch)
fi
