#!/usr/bin/env bash
# The first-microservice walkthrough, timed step by step: run from source, test, package, containerise, call.
# Needs a JDK 25 (java, javac, jar on the PATH or JAVA_HOME) and curl; docker for the last steps (skipped without it).
# JRE_IMAGE overrides the image's base (default eclipse-temurin:25-jre).
set -euo pipefail
cd "$(dirname "$0")/.."
J=${JAVA_HOME:+$JAVA_HOME/bin/}
unset JAVA_TOOL_OPTIONS
T0=$(date +%s.%N)
step() { printf '%-46s %6.2f s\n' "$1" "$(echo "$(date +%s.%N) - $2" | bc)"; }
wait_up() { for _ in $(seq 1 500); do curl -sf "$1" > /dev/null && return 0; sleep 0.01; done; return 1; }
"${J}java" -version 2>&1 | head -1

# 1. run straight from source: the JDK's source launcher compiles in memory (JEP 330, multi-file since JEP 458)
t=$(date +%s.%N)
PORT=18080 "${J}java" src/main/java/dev/foodfleet/menu/MenuService.java > /dev/null & PID=$!
wait_up localhost:18080/health
step "1. java MenuService.java, until /health" "$t"
curl -s localhost:18080/restaurants/r-1/menu?veg=true; echo
curl -s -i localhost:18080/restaurants/r-9/menu | grep -i -E '^HTTP|^content-type|^\{'; echo
kill $PID; wait $PID 2>/dev/null || true

# 2. compile and test
t=$(date +%s.%N)
rm -rf build && mkdir -p build/main build/test
"${J}javac" -d build/main $(find src/main/java -name '*.java')
"${J}javac" -d build/test -cp build/main $(find src/test/java -name '*.java')
"${J}java" -cp build/main:build/test dev.foodfleet.menu.MenuServiceTest
step "2. compile and run the tests" "$t"

# 3. package
t=$(date +%s.%N)
"${J}jar" --create --file build/menu-service.jar --main-class dev.foodfleet.menu.MenuService -C build/main .
step "3. jar ($(stat -c %s build/menu-service.jar) bytes)" "$t"
t=$(date +%s.%N)
PORT=18081 "${J}java" -jar build/menu-service.jar > /dev/null & PID=$!
wait_up localhost:18081/health
step "   java -jar, until /health" "$t"
echo "   memory (RSS) after the first request: $(curl -s localhost:18081/restaurants/r-1/menu > /dev/null; grep VmRSS /proc/$PID/status | awk '{print $2/1024 " MB"}')"
kill $PID; wait $PID 2>/dev/null || true

# 4. container
if command -v docker > /dev/null && docker info > /dev/null 2>&1; then
  t=$(date +%s.%N)
  docker build -q --build-arg JRE_IMAGE=${JRE_IMAGE:-eclipse-temurin:25-jre} -t foodfleet/menu-service:0.1.0 . > /dev/null
  step "4. docker build" "$t"
  echo "   image: $(docker image inspect foodfleet/menu-service:0.1.0 --format '{{.Size}}' | awk '{printf "%.0f MB", $1/1e6}'), base ${JRE_IMAGE:-eclipse-temurin:25-jre}"
  docker rm -f menu-service > /dev/null 2>&1 || true
  t=$(date +%s.%N)
  docker run -d --name menu-service -p 18082:8080 --memory 256m --cpus 1 foodfleet/menu-service:0.1.0 > /dev/null
  wait_up localhost:18082/health
  step "5. docker run, until /health" "$t"
  curl -s localhost:18082/restaurants/r-2/menu; echo
  echo "   runs as user: $(docker inspect menu-service --format '{{.Config.User}}')"
  docker rm -f menu-service > /dev/null
else
  echo "4-5. docker not available: skipped"
fi
step "all steps" "$T0"
