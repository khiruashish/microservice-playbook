#!/usr/bin/env bash
# Chapter 38 measurements and checks.
#   ./run.sh                 all experiments, then the Spring API checks
#   ./run.sh rotation        one experiment: handshake rejections resumption rotation binding tokens
#   SPRING_SECURITY_SRC=... SPRING_BOOT_SRC=... SPRING_FRAMEWORK_SRC=... ./run.sh   use existing checkouts
# The experiments need only a JDK (21+): the SPIFFE, mutual-TLS, policy and token code (pki/, spiffe/, authz/,
# token/) has no framework dependencies; everything runs on loopback with real TLS 1.3 and certificates from
# pki/DevCa. The API checks need git and access to github.com (sparse clones of the three Spring projects).
set -euo pipefail
cd "$(dirname "$0")"
SRC=../src/main/java
rm -rf build/main build/tools && mkdir -p build/main build/tools
javac -d build/main $(find $SRC -name '*.java' -not -path '*/web/*')
javac -d build/tools -cp build/main tools/Measure.java
CP=build/main:build/tools
for m in ${1:-handshake rejections resumption rotation binding tokens}; do
  echo "== $m"
  java -cp $CP Measure $m
  if [ "$m" = resumption ]; then
    java -Djdk.tls.server.enableSessionTicketExtension=false -cp $CP Measure resumption stateful
  fi
  echo
done
[ $# -gt 0 ] && exit 0

SS=${SPRING_SECURITY_SRC:-build/spring-security}
BOOT=${SPRING_BOOT_SRC:-build/spring-boot}
SF=${SPRING_FRAMEWORK_SRC:-build/spring-framework}
clone() {   # repo tag dir paths...
  local repo=$1 tag=$2 dir=$3; shift 3
  if [ ! -d "$dir/.git" ]; then
    git -c advice.detachedHead=false clone -q --depth 1 --branch "$tag" --filter=blob:none --sparse \
      "https://github.com/spring-projects/$repo.git" "$dir"
    git -C "$dir" sparse-checkout set "$@"
  fi
}
clone spring-security 7.1.1 "$SS" config/src/main core/src/main web/src/main oauth2
clone spring-boot v4.1.1 "$BOOT" core/spring-boot/src/main core/spring-boot-autoconfigure/src/main \
  module/spring-boot-web-server/src/main module/spring-boot-tomcat/src/main module/spring-boot-restclient/src/main \
  module/spring-boot-security-oauth2-client/src/main module/spring-boot-security-oauth2-resource-server/src/main \
  starter/spring-boot-starter-restclient starter/spring-boot-starter-security-oauth2-client
clone spring-framework v7.0.9 "$SF" spring-web/src/main/java/org/springframework/web/client \
  spring-web/src/main/java/org/springframework/web/filter
echo "== apis"
./check_apis.sh "$SS" "$BOOT" "$SF"
