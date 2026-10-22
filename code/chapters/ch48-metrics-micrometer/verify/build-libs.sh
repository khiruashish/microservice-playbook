#!/usr/bin/env bash
# Builds what chapter 48's experiments run from source, at the versions Spring Boot 4.1.1 manages (Maven Central
# isn't needed): Micrometer 1.17.1 (commons, observation, core without its optional third-party binders, the
# Prometheus registry), Prometheus Java client 1.7.0 (model, config, core, text formats), HdrHistogram 2.2.2,
# LatencyUtils 2.0.3, context-propagation 1.2.1; and, for the experiments' use of Spring's HTTP observation
# conventions, Spring Framework 7.0.9's spring-web and spring-core sources with Jakarta Servlet 6.1 and
# Commons Logging 1.3.6. Also downloads Prometheus 3.15.0 (Linux x86-64) and sparse-clones Spring Boot 4.1.1 for
# check_apis.sh. Output: build/lib/classes (runtime), build/lib/compile (compile-only), build/bin, build/src.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C48_SOURCES:-build/src}
JAVAC=${JAVAC:-javac}
mkdir -p "$SRC" build/bin build/lib
[ -f build/lib/.done ] && exit 0

clone() {   # dir, repo, tag, [sparse paths...]
  local dir=$SRC/$1 repo=$2 tag=$3; shift 3
  [ -d "$dir" ] && return
  if [ $# -gt 0 ]; then
    git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "https://github.com/$repo" "$dir" 2>/dev/null
    (cd "$dir" && git sparse-checkout set --no-cone "$@")
  else
    git clone -q --depth 1 --branch "$tag" "https://github.com/$repo" "$dir" 2>/dev/null
  fi
}
clone mm micrometer-metrics/micrometer v1.17.1 '/micrometer-commons/src/main/' '/micrometer-observation/src/main/' \
  '/micrometer-core/src/main/' '/implementations/micrometer-registry-prometheus/src/main/'
clone pc prometheus/client_java v1.7.0 '/prometheus-metrics-*/src/main/' '/prometheus-metrics-tracer/*/src/main/'
clone hdr HdrHistogram/HdrHistogram HdrHistogram-2.2.2 '/src/main/java/'
clone lu LatencyUtils/LatencyUtils LatencyUtils-2.0.3 '/src/main/java/'
clone cp micrometer-metrics/context-propagation v1.2.1 '/context-propagation/src/main/java/'
clone jspecify jspecify/jspecify v1.0.0 '/src/main/java/'
clone jsr305 amaembo/jsr-305 master '/ri/src/main/java/'
clone slf4j qos-ch/slf4j v_2.0.17 '/slf4j-api/src/main/java/'
clone cl apache/commons-logging rel/commons-logging-1.3.6 '/src/main/java/'
clone servlet jakartaee/servlet 6.1.0-RELEASE '/api/src/main/java/'
clone sf spring-projects/spring-framework v7.0.9 '/spring-core/src/main/java/' '/spring-web/src/main/java/' \
  '/spring-beans/src/main/java/' '/spring-context/src/main/java/org/springframework/context/annotation/' \
  '/spring-context/src/main/java/org/springframework/stereotype/'
clone boot spring-projects/spring-boot v4.1.1 '/module/spring-boot-micrometer-metrics/src/main/' \
  '/module/spring-boot-micrometer-observation/src/main/' \
  '/module/spring-boot-webmvc/src/main/java/org/springframework/boot/webmvc/autoconfigure/WebMvcObservationAutoConfiguration.java' \
  '/platform/spring-boot-dependencies/build.gradle'
rm -f "$SRC/servlet/api/src/main/java/module-info.java"

if [ ! -x build/bin/prometheus ]; then
  curl -sSfL -o build/bin/prom.tgz https://github.com/prometheus/prometheus/releases/download/v3.15.0/prometheus-3.15.0.linux-amd64.tar.gz
  tar xzf build/bin/prom.tgz -C build/bin --strip-components=1 prometheus-3.15.0.linux-amd64/prometheus \
    prometheus-3.15.0.linux-amd64/promtool && rm build/bin/prom.tgz
fi

# Kotlin placeholders: spring-core's Kotlin support is referenced from signatures javac reads, never called here
K=build/lib/kotlin-src
for c in kotlin/Unit kotlin/jvm/JvmClassMappingKt kotlin/reflect/KClass kotlin/reflect/KFunction kotlin/reflect/KParameter \
         kotlin/reflect/KProperty kotlin/reflect/KType kotlin/reflect/full/KClasses kotlin/reflect/jvm/ReflectJvmMapping; do
  mkdir -p "$K/$(dirname $c)"
  echo "package $(dirname $c | tr / .); public class $(basename $c) {}" > "$K/$c.java"
done

# compile-only: annotations, the tracer API, SLF4J's API (Micrometer falls back to java.util.logging without it),
# and stand-ins for the OpenTelemetry tracer bridges the Prometheus client looks for and doesn't find
ST=build/lib/stub-src/io/prometheus/metrics/tracer
mkdir -p "$ST/otel" "$ST/agent"
for c in otel/OpenTelemetrySpanContext agent/OpenTelemetryAgentSpanContext; do
  cat > "$ST/$c.java" <<JAVA
package io.prometheus.metrics.tracer.$(dirname $c);
public class $(basename $c) implements io.prometheus.metrics.tracer.common.SpanContext {
  public static boolean isAvailable() { return false; }
  public String getCurrentTraceId() { return null; }
  public String getCurrentSpanId() { return null; }
  public boolean isCurrentSpanSampled() { return false; }
  public void markCurrentSpanAsExemplar() { }
}
JAVA
done
rm -rf build/lib/compile build/lib/classes && mkdir -p build/lib/compile build/lib/classes
$JAVAC --release 21 -nowarn -Xlint:none -proc:none -d build/lib/compile \
  $(find "$SRC/pc/prometheus-metrics-annotations/src/main/java" "$SRC/pc/prometheus-metrics-tracer/prometheus-metrics-tracer-common/src/main/java" \
         "$SRC/jsr305/ri/src/main/java" "$SRC/slf4j/slf4j-api/src/main/java" build/lib/stub-src -name '*.java' | grep -v module-info)

# runtime: every library source file whose imports stay within the JDK and these libraries
python3 - "$SRC" > build/lib/sources.txt <<'PY'
import os, re, sys
src = sys.argv[1]
ok = re.compile(r'^(java|javax\.management|javax\.annotation|io\.micrometer|org\.jspecify|org\.HdrHistogram|org\.LatencyUtils|io\.prometheus|org\.slf4j|com\.sun\.management|jdk\.)')
roots = ['jspecify/src/main/java', 'jsr305/ri/src/main/java', 'hdr/src/main/java', 'lu/src/main/java', 'cp/context-propagation/src/main/java',
         'mm/micrometer-commons/src/main/java', 'mm/micrometer-observation/src/main/java', 'mm/micrometer-core/src/main/java',
         'mm/implementations/micrometer-registry-prometheus/src/main/java'] + \
        ['pc/%s/src/main/java' % m for m in ('prometheus-metrics-model', 'prometheus-metrics-config', 'prometheus-metrics-core',
         'prometheus-metrics-exposition-textformats', 'prometheus-metrics-annotations',
         'prometheus-metrics-tracer/prometheus-metrics-tracer-common', 'prometheus-metrics-tracer/prometheus-metrics-tracer-initializer')]
for r in roots:
    for d, _, fs in os.walk(os.path.join(src, r)):
        for f in fs:
            if not f.endswith('.java') or f == 'module-info.java':
                continue
            p = os.path.join(d, f)
            imports = [m.group(2) for m in (re.match(r'import\s+(static\s+)?([\w.]+)', l) for l in open(p, encoding='utf-8')) if m]
            if all(ok.match(i) for i in imports):
                print(p)
PY
# then drop, round by round, the files that still need an optional dependency (binders for Jetty, gRPC, servlets...)
: > build/lib/excluded.txt
for round in 1 2 3 4 5 6; do
  grep -vxFf build/lib/excluded.txt build/lib/sources.txt > build/lib/use.txt
  rm -rf build/lib/classes && mkdir -p build/lib/classes
  if $JAVAC --release 21 -nowarn -proc:none -cp build/lib/compile -d build/lib/classes @build/lib/use.txt > build/lib/javac.log 2>&1; then
    break
  fi
  grep 'error:' build/lib/javac.log | sed 's/:[0-9]*: error.*//' | sort -u >> build/lib/excluded.txt
done
grep -q 'error:' build/lib/javac.log && { cat build/lib/javac.log; exit 1; }
echo "$(wc -l < build/lib/use.txt) library sources compiled, $(wc -l < build/lib/excluded.txt) optional ones left out" > build/lib/.done
cat build/lib/.done
