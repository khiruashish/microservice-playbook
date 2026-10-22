#!/usr/bin/env bash
# Lab 17: chapter 60's menu-service started three ways on the host JVM, 10 starts each, time to the first 200:
#   jvm     nothing extra
#   appcds  an AppCDS archive written at the end of a training run (-XX:ArchiveClassesAtExit)
#   aot     JDK 25's AOT cache from the same training run (-XX:AOTCacheOutput): classes loaded and linked, profiles
# The training run is chapter 60's Trainer: 3,000 requests over HTTP. Needs a JDK 25 and the service's libraries
# (LIBS: Logback, Micrometer, Prometheus client; chapter 60's build-libs.sh collects them). About 2 minutes.
set -euo pipefail
unset JAVA_TOOL_OPTIONS
cd "$(dirname "$0")"
J=${JAVA_HOME:+$JAVA_HOME/bin/}
: "${LIBS:?set LIBS to a directory holding the service jars}"
CH60=../../../chapters/ch60-native-images-startup
W=build; rm -rf $W && mkdir -p $W/classes $W/app
${J}javac --release 25 -nowarn -XDsuppressNotes -d $W/classes -cp "$LIBS/*" \
  $(find $CH60/src/main/java -name '*.java') $CH60/verify/tools/StartupLab.java $CH60/verify/tools/Trainer.java
cp $CH60/src/main/resources/logback.xml $W/classes/
${J}jar --create --file $W/app/menu-service.jar -C $W/classes .
cp "$LIBS"/*.jar $W/app/
CP="$W/app/*"
${J}java -version 2>&1 | head -1

${J}java -XX:ArchiveClassesAtExit=$W/app.jsa -cp "$CP" Trainer > $W/train-cds.log 2>&1
s=$(date +%s%N); ${J}java -XX:AOTCacheOutput=$W/app.aot -cp "$CP" Trainer > $W/train-aot.log 2>&1
echo "training with -XX:AOTCacheOutput: $(( ($(date +%s%N) - s) / 1000000 )) ms; cache $(du -m $W/app.aot | cut -f1) MB, AppCDS archive $(du -m $W/app.jsa | cut -f1) MB"

URL=http://127.0.0.1:8080/menu?r=1
for v in jvm appcds aot; do
  case $v in
    jvm)    FLAGS=() ;;
    appcds) FLAGS=(-XX:SharedArchiveFile=$W/app.jsa) ;;
    aot)    FLAGS=(-XX:AOTCache=$W/app.aot) ;;
  esac
  printf '%-7s ' $v
  ${J}java StartTimer.java 10 $URL ${J}java "${FLAGS[@]}" -cp "$CP" StartupLab
done
