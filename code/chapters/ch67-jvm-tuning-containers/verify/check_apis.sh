#!/usr/bin/env bash
# Checks the JVM behaviours chapter 67 relies on against the JDK 25.0.4 sources (a sparse clone made by build-libs.sh).
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
H=$SRC/jdk/src/hotspot; JB=$SRC/jdk/src/java.base/share/classes

echo "-- ergonomics"
check "a 'server-class machine' needs 2 processors..."                 $H/share/runtime/os.cpp 'server_processors += 2;'
check "...and 2 GB of memory..."                                      $H/share/runtime/os.cpp 'server_memory += 2UL \* G;'
check "...less 256 MB allowed to be 'missing' (so 1792 MB qualifies)"   $H/share/runtime/os.cpp 'missing_memory += 256UL \* M;'
check "server class: G1; otherwise: Serial"                            $H/share/gc/shared/gcConfig.cpp 'FLAG_SET_ERGO_IF_DEFAULT\(UseSerialGC, true\);'
check "the JVM reads the container's limits (UseContainerSupport on)"  $H/os/linux/globals_linux.hpp 'product\(bool, UseContainerSupport, true'
check "...cgroup v2: memory.max"                                      $H/os/linux/cgroupV2Subsystem_linux.cpp '"/memory.max"'
check "ActiveProcessorCount overrides the CPUs it sees"               $H/share/gc/shared/gc_globals.hpp 'product\(int, ActiveProcessorCount, -1'
check "ForkJoinPool's common pool: CPUs - 1 (at least 1)"              $JB/java/util/concurrent/ForkJoinPool.java 'Math\.max\(1, Runtime\.getRuntime\(\)\.availableProcessors\(\) - 1\)'

echo "-- heap"
check "MaxRAMPercentage: 25% of the container's memory"                $H/share/gc/shared/gc_globals.hpp 'product\(double, MaxRAMPercentage, 25\.0'
check "MinRAMPercentage: 50% when memory is small"                     $H/share/gc/shared/gc_globals.hpp 'product\(double, MinRAMPercentage, 50\.0'
check "InitialRAMPercentage: 1.5625%"                                  $H/share/gc/shared/gc_globals.hpp 'product\(double, InitialRAMPercentage, 1\.5625'
check "direct buffers may use as much as the maximum heap, besides it" $JB/jdk/internal/misc/VM.java 'directMemory = Runtime\.getRuntime\(\)\.maxMemory\(\);'
check "ExitOnOutOfMemoryError exists (off by default)"                 $H/share/runtime/globals.hpp 'product\(bool, ExitOnOutOfMemoryError, false'

echo "-- collectors"
check "G1's pause-time goal: 200 ms"                                    $H/share/gc/g1/g1Arguments.cpp 'FLAG_SET_DEFAULT\(MaxGCPauseMillis, 200\);'
check "ZGC is generational only (ZGenerational obsolete in JDK 24)"     $H/share/runtime/arguments.cpp '"ZGenerational", +JDK_Version::jdk\(23\), JDK_Version::jdk\(24\)'
check "compact object headers are a product option, off by default"     $H/share/runtime/globals.hpp 'product\(bool, UseCompactObjectHeaders, false'

echo "-- Flight Recorder"
check "the default settings record every GC (threshold 0 ms)"           $SRC/jdk/src/jdk.jfr/share/conf/jfr/default.jfc '<event name="jdk.GarbageCollection">'
check "a profile settings file exists"                                  $SRC/jdk/src/jdk.jfr/share/conf/jfr/profile.jfc 'label="Profiling"'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
