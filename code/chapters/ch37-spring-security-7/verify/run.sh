#!/usr/bin/env bash
# Chapter 37 checks. Needs a JDK (21+), git and access to github.com.
#   ./run.sh              fetches the Spring Security 7.1.1 and Spring Boot 4.1.1 sources (sparse) into build/
#   SPRING_SECURITY_SRC=... SPRING_BOOT_SRC=... ./run.sh   uses existing checkouts
# 1. Compiles spring-security-crypto from the 7.1.1 sources (with the tiny stand-ins in stubs/) and measures it.
# 2. Checks that every Spring API and property this module uses exists in those sources (check_apis.sh).
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build
SS=${SPRING_SECURITY_SRC:-build/spring-security}
BOOT=${SPRING_BOOT_SRC:-build/spring-boot}
if [ ! -d "$SS/crypto" ]; then
  git -c advice.detachedHead=false clone -q --depth 1 --branch 7.1.1 --filter=blob:none --sparse https://github.com/spring-projects/spring-security.git "$SS"
  git -C "$SS" sparse-checkout set crypto/src/main config/src/main oauth2 core/src/main web/src/main test/src/main
fi
if [ ! -d "$BOOT/module" ]; then
  git -c advice.detachedHead=false clone -q --depth 1 --branch v4.1.1 --filter=blob:none --sparse https://github.com/spring-projects/spring-boot.git "$BOOT"
  git -C "$BOOT" sparse-checkout set module/spring-boot-security-oauth2-resource-server \
    module/spring-boot-security-oauth2-authorization-server module/spring-boot-webmvc-test platform
fi
C=$SS/crypto/src/main/java/org/springframework/security/crypto
rm -rf build/crypto build/tools && mkdir -p build/crypto build/tools
javac -d build/crypto $(find stubs -name '*.java') \
  $(find $C/password $C/bcrypt $C/codec $C/keygen $C/util -name '*.java' ! -name 'Password4j*')
javac -d build/tools -cp build/crypto tools/Measure.java
for m in encoders upgrade; do
  echo "== $m"
  java -cp build/crypto:build/tools Measure $m
  echo
done
echo "== apis"
./check_apis.sh "$SS" "$BOOT"
