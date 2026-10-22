#!/usr/bin/env bash
# Downloads and builds what chapter 51's experiments run: Prometheus 3.15.0 (promtool's rule unit tests are the rule
# engine) and Alertmanager 0.34.1 for Linux x86-64 from their GitHub releases; Micrometer 1.17.1 with its Prometheus
# registry, built from source by chapter 48's build-libs.sh; and sparse clones of Prometheus, Alertmanager, Micrometer
# and Spring Boot 4.1.1 for check_apis.sh.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C51_SOURCES:-build/src}
mkdir -p "$SRC" build/bin build/lib
[ -f build/lib/.done ] && exit 0

clone() {   # dir, repo, tag, sparse paths...
  local dir=$SRC/$1 repo=$2 tag=$3; shift 3
  [ -d "$dir" ] && return
  git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "https://github.com/$repo" "$dir" 2>/dev/null
  (cd "$dir" && git sparse-checkout set --no-cone "$@")
}
clone prom prometheus/prometheus v3.15.0 '/promql/functions.go' '/promql/quantile.go' '/promql/promqltest/test.go' \
  '/cmd/prometheus/main.go' '/rules/alerting.go'
clone am prometheus/alertmanager v0.34.1 '/config/config.go' '/inhibit/inhibit.go' '/dispatch/route.go'
clone mm micrometer-metrics/micrometer v1.17.1 '/micrometer-core/src/main/java/io/micrometer/core/instrument/AbstractTimerBuilder.java' \
  '/micrometer-core/src/main/java/io/micrometer/core/instrument/distribution/PercentileHistogramBuckets.java'
clone boot spring-projects/spring-boot v4.1.1 \
  '/module/spring-boot-micrometer-metrics/src/main/java/org/springframework/boot/micrometer/metrics/autoconfigure/MetricsProperties.java' \
  '/module/spring-boot-micrometer-metrics/src/main/java/org/springframework/boot/micrometer/metrics/autoconfigure/PropertiesMeterFilter.java'

fetch() { [ -s "$2" ] || curl -sSfL -o "$2" "$1"; }
if [ ! -x build/bin/promtool ]; then
  fetch https://github.com/prometheus/prometheus/releases/download/v3.15.0/prometheus-3.15.0.linux-amd64.tar.gz build/bin/prom.tgz
  tar xzf build/bin/prom.tgz -C build/bin --strip-components=1 prometheus-3.15.0.linux-amd64/promtool && rm build/bin/prom.tgz
fi
if [ ! -x build/bin/alertmanager ]; then
  fetch https://github.com/prometheus/alertmanager/releases/download/v0.34.1/alertmanager-0.34.1.linux-amd64.tar.gz build/bin/am.tgz
  tar xzf build/bin/am.tgz -C build/bin --strip-components=1 alertmanager-0.34.1.linux-amd64/alertmanager \
    alertmanager-0.34.1.linux-amd64/amtool && rm build/bin/am.tgz
fi

# Micrometer and the Prometheus client, as chapter 48 builds them
../../ch48-metrics-micrometer/verify/build-libs.sh > /dev/null
rm -f build/lib/micrometer && ln -s "$(cd ../../ch48-metrics-micrometer/verify/build/lib/classes && pwd)" build/lib/micrometer
echo "promtool, alertmanager, amtool; Micrometer from chapter 48: $(find -L build/lib/micrometer -name '*.class' | wc -l) classes" | tee build/lib/.done
