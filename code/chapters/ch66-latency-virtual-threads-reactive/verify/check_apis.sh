#!/usr/bin/env bash
# Checks the runtime behaviours chapter 66 relies on against their sources (sparse clones made by build-libs.sh):
# JDK 25.0.4 and 21.0.10, Spring Boot 4.1.1, Reactor Netty 1.3.7 and Reactor Core 3.8.7.
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
J25=$SRC/jdk/src; J21=$SRC/jdk21/src

echo "-- virtual threads and monitors"
check "JDK 21: a virtual thread blocked in synchronized pins its carrier (tracePinnedThreads to report it)" $J21/java.base/share/classes/java/lang/VirtualThread.java 'privilegedGetProperty\("jdk\.tracePinnedThreads"\)'
check "JDK 25: virtual threads unmount while blocked on a monitor (JEP 491)" $J25/java.base/share/classes/java/lang/VirtualThread.java 'BLOCKED   = 13;        // unmounted'
check "JDK 25: still pinned by native frames and critical sections"     $J25/java.base/share/classes/jdk/internal/vm/Continuation.java 'Native frame on stack \*/ NATIVE'
check "JDK 25: pinning is reported by the JFR event jdk.VirtualThreadPinned" $J25/java.base/share/classes/java/lang/VirtualThread.java 'JFR jdk\.VirtualThreadPinned event'

echo "-- JDK 25 HTTP server"
check "keeps at most 200 idle keep-alive connections by default"         $J25/jdk.httpserver/share/classes/sun/net/httpserver/ServerConfig.java 'DEFAULT_MAX_IDLE_CONNECTIONS = 200'
check "...set with sun.net.httpserver.maxIdleConnections"                $J25/jdk.httpserver/share/classes/sun/net/httpserver/ServerConfig.java 'Integer\.getInteger\("sun\.net\.httpserver\.maxIdleConnections"'

echo "-- Spring Boot 4.1.1"
check "spring.threads.virtual.enabled switches to virtual threads"       $SRC/boot/core/spring-boot/src/main/java/org/springframework/boot/thread/Threading.java 'getProperty\("spring\.threads\.virtual\.enabled", boolean\.class, false\)'
check "Tomcat's request threads: at most 200 by default"                 $SRC/boot/module/spring-boot-tomcat/src/main/java/org/springframework/boot/tomcat/autoconfigure/TomcatServerProperties.java 'private int max = 200;'
check "...with virtual threads, Tomcat's executor is a VirtualThreadExecutor (no thread limit)" $SRC/boot/module/spring-boot-tomcat/src/main/java/org/springframework/boot/tomcat/autoconfigure/TomcatVirtualThreadsWebServerFactoryCustomizer.java 'setExecutor\(new VirtualThreadExecutor\("tomcat-handler-"\)\)'
check "...leaving max-connections (8192) as the only limit"               $SRC/boot/module/spring-boot-tomcat/src/main/java/org/springframework/boot/tomcat/autoconfigure/TomcatServerProperties.java 'private int maxConnections = 8192;'

echo "-- Reactor"
check "Reactor Netty: event-loop threads = max(CPUs, 4)"                 $SRC/rnetty/reactor-netty-core/src/main/java/reactor/netty/resources/LoopResources.java 'Math\.max\(Runtime\.getRuntime\(\)\.availableProcessors\(\), 4\)'
check "Reactor: boundedElastic (for blocking calls) = 10 x CPUs threads" $SRC/rcore/reactor-core/src/main/java/reactor/core/scheduler/Schedulers.java 'orElseGet\(\(\) -> 10 \* Runtime\.getRuntime\(\)\.availableProcessors\(\)\)'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
