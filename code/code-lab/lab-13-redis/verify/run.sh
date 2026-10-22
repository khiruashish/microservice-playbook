#!/usr/bin/env bash
# Lab 13: starts a throwaway Redis (redis-server on the PATH, 7.0+) on port 56379 and runs redis_lab.py. About 30 s.
set -euo pipefail
unset JAVA_TOOL_OPTIONS
cd "$(dirname "$0")"
D=$(mktemp -d)
redis-server --port 56379 --save '' --appendonly no --dir "$D" --daemonize yes --logfile "$D/redis.log"
trap 'redis-cli -p 56379 shutdown nosave >/dev/null 2>&1 || true; rm -rf "$D"' EXIT
until redis-cli -p 56379 ping >/dev/null 2>&1; do sleep 0.1; done
redis-server --version | cut -d' ' -f1-3
REDIS_PORT=56379 python3 redis_lab.py "$@"
echo
# MenuCards is plain Java: compile it with the check, no Spring, no Redis.
J=${JAVA_HOME:+$JAVA_HOME/bin/}
B=$D/classes
${J}javac -d "$B" ../src/main/java/dev/foodfleet/lab13/{MenuCards,SharedCache}.java MenuCardsCheck.java
${J}java -cp "$B" MenuCardsCheck
