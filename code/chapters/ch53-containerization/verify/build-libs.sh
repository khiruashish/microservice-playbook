#!/usr/bin/env bash
# Builds and downloads what chapter 53's experiments run: menu-service's libraries (Micrometer 1.17.1 with the
# Prometheus client, from chapter 48's build; SLF4J 2.0.18 and Logback 1.5.38, from chapter 50's), split one directory
# per jar; the distribution registry 3.1.2, crane 0.22.1, syft 1.54.1 and the Jib CLI 0.13.0 from their GitHub
# releases; and sparse clones of OpenJDK 25.0.4, Spring Boot 4.1.1 and Spring Framework 7.0.9 for check_apis.sh.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C53_SOURCES:-build/src}
mkdir -p "$SRC" build/bin build/lib
[ -f build/lib/.done ] && exit 0

clone() {   # dir, repo, tag, sparse paths...
  local dir=$SRC/$1 repo=$2 tag=$3; shift 3
  [ -d "$dir" ] && return
  git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "https://github.com/$repo" "$dir" 2>/dev/null
  (cd "$dir" && git sparse-checkout set --no-cone "$@")
}
clone jdk openjdk/jdk25u jdk-25.0.4+7 '/src/hotspot/share/gc/shared/gc_globals.hpp' '/src/hotspot/share/runtime/os.cpp' \
  '/src/hotspot/share/gc/shared/gcConfig.cpp' '/src/hotspot/share/runtime/arguments.cpp'
clone boot spring-projects/spring-boot v4.1.1 '/loader/spring-boot-jarmode-tools/src/main/java/org/springframework/boot/jarmode/tools/ExtractCommand.java'
clone sf spring-projects/spring-framework v7.0.9 '/spring-context/src/main/java/org/springframework/context/support/DefaultLifecycleProcessor.java'

GH=https://github.com
fetch() { [ -s "$2" ] || curl -sSfL -o "$2" "$1"; }
fetch $GH/distribution/distribution/releases/download/v3.1.2/registry_3.1.2_linux_amd64.tar.gz build/bin/registry.tgz
fetch $GH/google/go-containerregistry/releases/download/v0.22.1/go-containerregistry_Linux_x86_64.tar.gz build/bin/crane.tgz
fetch $GH/anchore/syft/releases/download/v1.54.1/syft_1.54.1_linux_amd64.tar.gz build/bin/syft.tgz
fetch $GH/GoogleContainerTools/jib/releases/download/v0.13.0-cli/jib-jre-0.13.0.zip build/bin/jib.zip
(cd build/bin && tar xzf registry.tgz registry && tar xzf crane.tgz crane && tar xzf syft.tgz syft && rm -rf jib && unzip -q jib.zip && mv jib-0.13.0 jib)

# menu-service's dependencies, as chapters 48 and 50 build them from source, one directory per jar
../../ch48-metrics-micrometer/verify/build-libs.sh > /dev/null
../../ch50-structured-logging/verify/build-libs.sh > /dev/null
M=../../ch48-metrics-micrometer/verify/build/lib/classes
L=../../ch50-structured-logging/verify/build/lib/logback
R=build/lib/deps
rm -rf $R && mkdir -p $R
put() { mkdir -p "$R/$1/$(dirname "$3")" && cp -r "$2/$3" "$R/$1/$(dirname "$3")/"; }
for d in common core observation prometheusmetrics; do put micrometer-1.17.1 $M io/micrometer/$d; done
put context-propagation-1.2.1 $M io/micrometer/context
put prometheus-metrics-1.7.0 $M io/prometheus
put HdrHistogram-2.2.2 $M org/HdrHistogram
put LatencyUtils-2.0.3 $M org/LatencyUtils
put jspecify-1.0.0 $M org/jspecify
put jsr305-3.0.2 $M javax
put slf4j-api-2.0.18 $L org/slf4j
put logback-1.5.38 $L ch
put logback-1.5.38 $L META-INF/services
echo "$(ls $R | wc -l) dependency directories" | tee build/lib/.done
