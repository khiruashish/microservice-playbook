#!/usr/bin/env bash
# Builds and downloads what chapter 49's experiments run: the OpenTelemetry API 1.66.0 (the version inside the agent)
# and jnats 2.26.4 from their release tags; the OpenTelemetry Java agent 2.32.0, the Collector Contrib 0.162.0 and
# nats-server 2.15.1 (Linux x86-64) from their GitHub releases; and sparse clones of the instrumentation 2.32.0,
# OpenTelemetry Java 1.66.0 and Spring Boot 4.1.1 sources for check_apis.sh.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C49_SOURCES:-build/src}
JAVAC=${JAVAC:-javac}
mkdir -p "$SRC" build/bin build/lib
[ -f build/lib/.done ] && exit 0

clone() {   # dir, repo, tag, sparse paths...
  local dir=$SRC/$1 repo=$2 tag=$3; shift 3
  [ -d "$dir" ] && return
  git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "https://github.com/$repo" "$dir" 2>/dev/null
  (cd "$dir" && git sparse-checkout set --no-cone "$@")
}
clone otel open-telemetry/opentelemetry-java v1.66.0 '/api/all/src/main/' '/context/src/main/' '/common/src/main/' \
  '/sdk/trace/src/main/java/io/opentelemetry/sdk/trace/samplers/' '/sdk-extensions/autoconfigure/src/main/java/'
clone jnats nats-io/nats.java 2.26.4 '/src/main/java/'
clone jsr305 amaembo/jsr-305 master '/ri/src/main/java/'
clone jspecify jspecify/jspecify v1.0.0 '/src/main/java/'
clone otelji open-telemetry/opentelemetry-java-instrumentation v2.32.0 '/instrumentation/kafka/' '/instrumentation/nats/' \
  '/instrumentation/executors/'
clone boot spring-projects/spring-boot v4.1.1 '/module/spring-boot-micrometer-tracing/src/main/' \
  '/module/spring-boot-micrometer-tracing-opentelemetry/src/main/' \
  '/module/spring-boot-kafka/src/main/java/org/springframework/boot/kafka/autoconfigure/KafkaProperties.java'

fetch() { [ -s "$2" ] || curl -sSfL -o "$2" "$1"; }
fetch https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.32.0/opentelemetry-javaagent.jar \
  build/bin/opentelemetry-javaagent.jar
if [ ! -x build/bin/otelcol-contrib ]; then
  fetch https://github.com/open-telemetry/opentelemetry-collector-releases/releases/download/v0.162.0/otelcol-contrib_0.162.0_linux_amd64.tar.gz build/bin/otelcol.tgz
  tar xzf build/bin/otelcol.tgz -C build/bin otelcol-contrib && rm build/bin/otelcol.tgz
fi
if [ ! -x build/bin/nats-server ]; then
  fetch https://github.com/nats-io/nats-server/releases/download/v2.15.1/nats-server-v2.15.1-linux-amd64.tar.gz build/bin/nats.tgz
  tar xzf build/bin/nats.tgz -C build/bin --strip-components=1 nats-server-v2.15.1-linux-amd64/nats-server && rm build/bin/nats.tgz
fi

rm -rf build/lib/compile build/lib/otel build/lib/jnats && mkdir -p build/lib/compile build/lib/otel build/lib/jnats
# compile-only: jsr305 and jspecify annotations, @AutoValue, @MustBeClosed, Bouncy Castle signatures
$JAVAC --release 21 -nowarn -Xlint:none -d build/lib/compile \
  $(find "$SRC/jsr305/ri/src/main/java" "$SRC/jspecify/src/main/java" lib-src/stubs -name '*.java' | grep -v module-info)
# the OpenTelemetry API, with lib-src/autovalue in place of AutoValue's generated classes
$JAVAC --release 21 -nowarn -Xlint:none -XDsuppressNotes -proc:none -cp build/lib/compile -d build/lib/otel \
  $(find "$SRC/otel/api/all/src/main/java" "$SRC/otel/context/src/main/java" "$SRC/otel/common/src/main/java" lib-src/autovalue \
         -name '*.java' | grep -v module-info)
# jnats; the Bouncy Castle stand-ins go with it (they throw if NKey authentication is ever used)
$JAVAC --release 21 -nowarn -Xlint:none -XDsuppressNotes -proc:none -cp build/lib/compile -d build/lib/jnats \
  $(find "$SRC/jnats/src/main/java" -name '*.java' | grep -v module-info)
$JAVAC --release 21 -nowarn -d build/lib/jnats $(find lib-src/stubs/org -name '*.java')
echo "OpenTelemetry API: $(find build/lib/otel -name '*.class' | wc -l) classes; jnats: $(find build/lib/jnats -name '*.class' | wc -l)" | tee build/lib/.done
