#!/usr/bin/env bash
# Lab 20's rehearsal, plain Java (no cluster), about 6 minutes: chapter 65's chaos experiment. order-service calls
# menu-service through a fault proxy at 50 orders a second; for 30 s menu-service is slow on half the calls, then
# hung. Three versions of order-service: no timeout, timeout + retry, timeout + last known price. Each run aborts
# itself when success drops below 90% in a 2-second window, as a game day's abort condition does.
set -euo pipefail
unset JAVA_TOOL_OPTIONS
cd "$(dirname "$0")"
J=${JAVA_HOME:+$JAVA_HOME/bin/}
C=../../../chapters/ch65-performance-chaos-arch-tests
W=build; rm -rf $W && mkdir -p $W/main $W/tools $W/work
${J}javac --release 25 -nowarn -d $W/main $(find $C/src/main/java -name '*.java')
${J}javac --release 25 -nowarn -d $W/tools -cp $W/main $C/verify/tools/Lab.java
(cd $C/verify && ${J}java -cp "$OLDPWD/$W/main:$OLDPWD/$W/tools" Lab ../src/main/java/dev/foodfleet/ch65/sample violations.tsv \
   "$OLDPWD/$W/work" chaos)
