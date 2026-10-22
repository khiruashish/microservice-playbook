#!/usr/bin/env bash
# Checks the JDK 25.0.4, GraalVM 25.0.2, CRaC (24+20) and Spring Framework 7.0.9 behaviours chapter 60 relies on
# against their sources (sparse clones made by build-libs.sh).
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
J=$SRC/jdk/src/hotspot/share/cds
G=$SRC/graal/substratevm/src/com.oracle.svm.core.genscavenge/src/com/oracle/svm/core/genscavenge
C=$SRC/crac/src
F=$SRC/sf/spring-context/src/main/java/org/springframework/context/support

echo "-- JDK 25.0.4: AOT cache (JEPs 483, 514, 515)"
check "-XX:AOTCacheOutput: one-step training run"                        $J/cds_globals.hpp '"Specifies the file name for writing the AOT cache"'
check "-XX:AOTCache: start from the cache"                                $J/cds_globals.hpp '"Cache for improving start up and warm up"'
check "...AOTCacheOutput implies AOTMode=record"                          $J/cdsConfig.cpp 'If AOTCacheOutput has been set, effective mode is "record"'
check "...classes are linked ahead of time when creating the cache"       $J/cdsConfig.cpp 'FLAG_SET_ERGO_IF_DEFAULT\(AOTClassLinking, true\)'
check "...method profiles recorded and replayed (AOTRecordTraining)"      $J/cds_globals.hpp 'product\(bool, AOTRecordTraining'
check "AOTMode defaults to auto: an unusable cache is skipped, not fatal" $J/cds_globals.hpp 'the default is auto"'
check "...AOTMode=on makes a cache that can't be used stop the JVM"      $J/cdsConfig.cpp 'assert\(strcmp\(AOTMode, "on"\) == 0, "already checked"\);'
check "-XX:ArchiveClassesAtExit: a dynamic AppCDS archive"                $J/cds_globals.hpp 'product\(ccstr, ArchiveClassesAtExit'

echo "-- GraalVM CE 25.0.2: native image heap"
check "maximum heap: 80% of memory by default"                            $G/SerialAndEpsilonGCOptions.java 'MaximumHeapSizePercent = new NotifyGCRuntimeOptionKey<>\(80'
check "young generation: 10% of the maximum heap by default"              $G/SerialAndEpsilonGCOptions.java 'MaximumYoungGenerationSizePercent = new NotifyGCRuntimeOptionKey<>\(10'

echo "-- CRaC (openjdk/crac, crac-24+20)"
check "-XX:CRaCCheckpointTo"                                              $C/hotspot/share/runtime/globals.hpp 'product\(ccstr, CRaCCheckpointTo'
check "-XX:CRaCRestoreFrom"                                               $C/hotspot/share/runtime/globals.hpp 'product\(ccstr, CRaCRestoreFrom'
check "...the default engine checkpoints with CRIU"                        $C/hotspot/share/runtime/globals.hpp 'product\(ccstr, CREngine, "criuengine"'
check "resources are notified before checkpoint and after restore"        $C/java.base/share/classes/jdk/crac/Resource.java 'void afterRestore\('

echo "-- Spring Framework 7.0.9"
check "spring.context.checkpoint=onRefresh: CRaC checkpoint at startup"   $F/DefaultLifecycleProcessor.java 'CHECKPOINT_PROPERTY_NAME = "spring\.context\.checkpoint"'
check "spring.context.exit=onRefresh: start the context, then exit"        $F/DefaultLifecycleProcessor.java 'EXIT_PROPERTY_NAME = "spring\.context\.exit"'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
