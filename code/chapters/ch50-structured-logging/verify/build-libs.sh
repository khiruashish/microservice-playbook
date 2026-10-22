#!/usr/bin/env bash
# Builds and downloads what chapter 50's experiments run: SLF4J 2.0.18 and Logback 1.5.38 (the versions Spring Boot
# 4.1.1 manages) from their release tags, the OpenTelemetry Java agent 2.32.0 from its GitHub release, and sparse
# clones of Spring Framework 7.0.9 (TaskDecorator), JSpecify, the instrumentation 2.32.0 and Spring Boot 4.1.1 for
# compiling and for check_apis.sh.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C50_SOURCES:-build/src}
JAVAC=${JAVAC:-javac}
mkdir -p "$SRC" build/bin build/lib
[ -f build/lib/.done ] && exit 0

clone() {   # dir, repo, tag, sparse paths...
  local dir=$SRC/$1 repo=$2 tag=$3; shift 3
  [ -d "$dir" ] && return
  git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "https://github.com/$repo" "$dir" 2>/dev/null
  (cd "$dir" && git sparse-checkout set --no-cone "$@")
}
clone slf4j qos-ch/slf4j v_2.0.18 '/slf4j-api/src/main/java/'
clone logback qos-ch/logback v_1.5.38 '/logback-core/src/main/' '/logback-classic/src/main/'
clone sf spring-projects/spring-framework v7.0.9 '/spring-core/src/main/java/org/springframework/core/task/TaskDecorator.java'
clone jspecify jspecify/jspecify v1.0.0 '/src/main/java/'
clone otelji open-telemetry/opentelemetry-java-instrumentation v2.32.0 '/instrumentation/logback/logback-mdc-1.0/' \
  '/instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/config/internal/' \
  '/instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/log/'
clone boot spring-projects/spring-boot v4.1.1 '/core/spring-boot/src/main/java/org/springframework/boot/logging/' \
  '/core/spring-boot/src/main/resources/'

[ -s build/bin/opentelemetry-javaagent.jar ] || curl -sSfL -o build/bin/opentelemetry-javaagent.jar \
  https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.32.0/opentelemetry-javaagent.jar

# SLF4J's API and Logback core + classic, leaving out the classes for optional dependencies (servlets, Janino, mail,
# Tomcat/Jetty access logs): anything importing outside the JDK, SLF4J or Logback, and what only compiles with them
rm -rf build/lib/logback && mkdir -p build/lib/logback
python3 - "$SRC" > build/lib/sources.txt <<'PY'
import os, re, sys
src = sys.argv[1]
ok = re.compile(r'^(java|javax\.net|javax\.naming|javax\.xml|javax\.management|org\.xml|org\.w3c|ch\.qos|org\.slf4j|sun\.)')
skip = ('SMTPAppender', 'XMLLayout')
for root in (f'{src}/slf4j/slf4j-api/src/main/java', f'{src}/logback/logback-core/src/main/java',
             f'{src}/logback/logback-classic/src/main/java'):
    for d, _, fs in os.walk(root):
        for f in fs:
            p = os.path.join(d, f)
            if not f.endswith('.java') or f == 'module-info.java' or any(s in f for s in skip):
                continue
            imps = [m.group(2) for m in (re.match(r'import\s+(static\s+)?([\w.]+)', l) for l in open(p, encoding='utf-8')) if m]
            if all(ok.match(i) for i in imps):
                print(p)
PY
for attempt in 1 2 3 4 5 6 7 8; do
  if $JAVAC --release 21 -nowarn -Xlint:none -XDsuppressNotes -proc:none -d build/lib/logback @build/lib/sources.txt \
       2> build/lib/javac.err; then break; fi
  # drop the files that fail (they need a class left out above) and try again
  grep -oE '^[^ ]+\.java' build/lib/javac.err | sort -u > build/lib/failed.txt
  [ -s build/lib/failed.txt ] || { cat build/lib/javac.err; exit 1; }
  grep -vxFf build/lib/failed.txt build/lib/sources.txt > build/lib/s2.txt && mv build/lib/s2.txt build/lib/sources.txt
  rm -rf build/lib/logback && mkdir -p build/lib/logback
done
mkdir -p build/lib/logback/META-INF/services
cp "$SRC/logback/logback-classic/src/main/resources/META-INF/services/org.slf4j.spi.SLF4JServiceProvider" build/lib/logback/META-INF/services/
for m in core classic; do
  f=$(find "$SRC/logback/logback-$m/src/main/resources" -name '*-version.properties')
  mkdir -p "build/lib/logback/$(dirname "${f#$SRC/logback/logback-$m/src/main/resources/}")"
  sed 's/\${project.version}/1.5.38/' "$f" > "build/lib/logback/${f#$SRC/logback/logback-$m/src/main/resources/}"
done
echo "SLF4J + Logback: $(find build/lib/logback -name '*.class' | wc -l) classes" | tee build/lib/.done
