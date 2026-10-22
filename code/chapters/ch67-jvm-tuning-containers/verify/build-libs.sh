#!/usr/bin/env bash
# Prepares chapter 67: JUnit 6 (chapter 61's build-libs.sh fetches it) and a sparse clone of the JDK 25.0.4 sources
# for check_apis.sh. Needs git and curl.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C67_SOURCES:-build/src}
mkdir -p "$SRC" build
C61_SOURCES="$(cd ../../ch61-test-strategy/verify && pwd)/build/src" ../../ch61-test-strategy/verify/build-libs.sh > /dev/null
ln -sfn "$(cd ../../ch61-test-strategy/verify/build/lib && pwd)" build/lib
if [ ! -d "$SRC/jdk" ]; then
  git clone -q --depth 1 --filter=blob:none --sparse --branch jdk-25.0.4+7 https://github.com/openjdk/jdk25u "$SRC/jdk" 2>/dev/null
  (cd "$SRC/jdk" && git sparse-checkout set --no-cone \
    /src/hotspot/share/runtime/os.cpp /src/hotspot/share/runtime/globals.hpp /src/hotspot/share/runtime/arguments.cpp \
    /src/hotspot/share/gc/shared/gcConfig.cpp /src/hotspot/share/gc/shared/gc_globals.hpp /src/hotspot/share/gc/g1/g1Arguments.cpp \
    /src/hotspot/os/linux/globals_linux.hpp /src/hotspot/os/linux/cgroupV2Subsystem_linux.cpp \
    /src/java.base/share/classes/jdk/internal/misc/VM.java /src/java.base/share/classes/java/util/concurrent/ForkJoinPool.java \
    /src/jdk.jfr/share/conf/jfr/default.jfc /src/jdk.jfr/share/conf/jfr/profile.jfc)
fi
