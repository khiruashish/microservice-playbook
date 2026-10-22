#!/usr/bin/env bash
# Which of the handbook's code can run on this machine? Reports the tools found, then, for every chapter, what its
# experiments need (read from the header of the chapter's verify/run.sh) and whether this machine has it.
# Chapters without verify/ are Maven modules: they need a JDK 25, Maven 3.9+ and access to Maven Central.
# Usage: tools/check-env.sh [--all]      (--all lists every chapter, not only the ones that can't run)
set -uo pipefail
cd "$(dirname "$0")/.."
J=${JAVA_HOME:+$JAVA_HOME/bin/}
declare -A HAVE VER
probe() {   # name, command that prints a version (empty output = missing)
  local out
  out=$(eval "$2" 2>/dev/null | head -1)
  if [ -n "$out" ]; then HAVE[$1]=1; VER[$1]=$out; else HAVE[$1]=0; VER[$1]="missing"; fi
}
probe jdk25    "${J}java -version 2>&1 | grep -E 'version \"25' "
probe jdk21    "\${JDK21_HOME:+\$JDK21_HOME/bin/java -version 2>&1 | grep -E 'version \"21'}"
probe maven    "mvn -v 2>/dev/null | grep -E 'Apache Maven 3\.(9|[1-9][0-9])'"
probe central  "curl -s -o /dev/null -w '%{http_code}' --max-time 5 https://repo.maven.apache.org/maven2/ | grep -E '^(200|301|302)$'"
probe git      "git --version"
probe curl     "curl --version"
probe docker   "docker info --format 'Docker {{.ServerVersion}}' 2>/dev/null"
probe cluster  "\${KUBECTL:-kubectl} version 2>/dev/null | grep -i server || ls code/chapters/ch52-health-checks-actuator/verify/build/k3s/k3s 2>/dev/null"
probe postgres "\${PG_BIN:-\$(pg_config --bindir 2>/dev/null)}/postgres --version 2>/dev/null | grep -E ' 1[6-9]\.'"
probe redis    "redis-server --version"
probe go       "go version"
probe python3  "python3 --version"
probe openssl  "openssl version"
probe unzip    "unzip -v | head -1"
probe x86      "uname -m | grep x86_64"
echo "Tools"
for t in jdk25 jdk21 maven central git curl docker cluster postgres redis go python3 openssl unzip x86; do
  printf '  %-9s %s %s\n' "$t" "$([ "${HAVE[$t]}" = 1 ] && echo '+' || echo '-')" "${VER[$t]}"
done
needs() {   # chapter dir -> space-separated tool names
  local f=$1/verify/run.sh h
  if [ ! -f "$f" ]; then
    # a Maven module; Testcontainers-based tests start containers too
    if grep -qi testcontainers "$1/pom.xml" 2>/dev/null; then echo "jdk25 maven central docker"; else echo "jdk25 maven central"; fi
    return
  fi
  h=$(sed -n '2,/^set -/p' "$f" | tr '[:upper:]' '[:lower:]')
  local n="jdk25"
  [[ $h == *docker* ]] && n+=" docker"
  [[ $h =~ (a|kubernetes|k3s)\ cluster || $h == *k3s* || $h == *kubectl* ]] && n+=" cluster"
  [[ $h == *postgres* ]] && n+=" postgres"
  [[ $h == *redis* ]] && n+=" redis"
  [[ $h =~ (^|[^a-z])go\ 1 || $h == *"go 1."* ]] && n+=" go"
  [[ $h == *python3* ]] && n+=" python3"
  [[ $h == *"jdk 21"* && $h == *jdk21_home* ]] && n+=" jdk21"
  [[ $h == *git* ]] && n+=" git"
  [[ $h == *curl* ]] && n+=" curl"
  [[ $h == *openssl* ]] && n+=" openssl"
  [[ $h == *unzip* ]] && n+=" unzip"
  [[ $h == *x86-64* ]] && n+=" x86"
  echo "$n"
}
echo "Chapters"
ok=0; total=0
for d in code/chapters/ch*/ code/getting-started/*/; do
  d=${d%/}; total=$((total + 1)); missing=""
  for t in $(needs "$d"); do [ "${HAVE[$t]}" = 1 ] || missing+=" $t"; done
  if [ -z "$missing" ]; then ok=$((ok + 1)); [ "${1:-}" = --all ] && printf '  + %s\n' "${d#code/}"
  else printf '  - %-50s missing:%s\n' "${d#code/}" "$missing"; fi
done
echo "$ok of $total can run here"
