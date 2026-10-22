#!/usr/bin/env bash
# Checks the JVM ergonomics and Spring defaults chapter 53 relies on against the sources of OpenJDK 25.0.4, Spring
# Boot 4.1.1 and Spring Framework 7.0.9 (sparse clones made by build-libs.sh).
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
H=$SRC/jdk/src/hotspot/share

echo "-- OpenJDK 25.0.4"
check "MaxRAMPercentage defaults to 25"                              $H/gc/shared/gc_globals.hpp 'product\(double, MaxRAMPercentage, 25\.0,'
check "MinRAMPercentage 50 (the heap share when memory is small)"      $H/gc/shared/gc_globals.hpp 'product\(double, MinRAMPercentage, 50\.0,'
check "...used when 50% of memory is below the default MaxHeapSize"   $H/runtime/arguments.cpp 'if \(reasonable_min < MaxHeapSize\) \{'
check "server-class machine: at least 2 processors..."               $H/runtime/os.cpp 'const unsigned int server_processors += 2;'
check "...and 2 GB of memory, less 256 MB allowed missing"            $H/runtime/os.cpp 'const physical_memory_size_type missing_memory  = 256UL \* M;'
check "G1 on server-class machines (otherwise Serial)"               $H/gc/shared/gcConfig.cpp 'if \(os::is_server_class_machine\(\)\) \{'

echo "-- Spring Boot 4.1.1 / Spring Framework 7.0.9"
check "java -Djarmode=tools extract --layers"                       $SRC/boot/loader/spring-boot-jarmode-tools/src/main/java/org/springframework/boot/jarmode/tools/ExtractCommand.java 'Option\.of\("layers", "string list", "Layers to extract", true\)'
check "...--launcher"                                                $SRC/boot/loader/spring-boot-jarmode-tools/src/main/java/org/springframework/boot/jarmode/tools/ExtractCommand.java 'Option\.flag\("launcher"'
check "-Dspring.context.exit=onRefresh for AOT/CDS training runs"   $SRC/sf/spring-context/src/main/java/org/springframework/context/support/DefaultLifecycleProcessor.java 'ON_REFRESH_VALUE = "onRefresh";'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
