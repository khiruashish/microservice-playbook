#!/usr/bin/env bash
# Prepares chapter 66: JUnit 6 (chapter 61's build-libs.sh fetches it), and sparse clones of the JDK 25 and 21 sources,
# Spring Boot, Reactor Netty and Reactor Core for check_apis.sh. Needs git, curl, a JDK 25, and a JDK 21 for the
# pinning experiment (JDK21_HOME).
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C66_SOURCES:-build/src}
mkdir -p "$SRC" build
C61_SOURCES="$(cd ../../ch61-test-strategy/verify && pwd)/build/src" ../../ch61-test-strategy/verify/build-libs.sh > /dev/null
ln -sfn "$(cd ../../ch61-test-strategy/verify/build/lib && pwd)" build/lib
clone() {   # repo, tag, dir, paths...
  local repo=$1 tag=$2 dir=$3; shift 3
  if [ ! -d "$dir" ]; then
    git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "$repo" "$dir" 2>/dev/null
    (cd "$dir" && git sparse-checkout set --no-cone "$@")
  fi
}
clone https://github.com/openjdk/jdk25u jdk-25.0.4+7 "$SRC/jdk" /src/jdk.httpserver/share/classes/sun/net/httpserver/ServerConfig.java \
  /src/java.base/share/classes/jdk/internal/vm/Continuation.java /src/java.base/share/classes/java/lang/VirtualThread.java
clone https://github.com/openjdk/jdk21u jdk-21.0.10+7 "$SRC/jdk21" /src/java.base/share/classes/java/lang/VirtualThread.java
clone https://github.com/spring-projects/spring-boot v4.1.1 "$SRC/boot" /core/spring-boot/src/main/java/org/springframework/boot/thread/ \
  /module/spring-boot-tomcat/src/main/java/org/springframework/boot/tomcat/autoconfigure/TomcatServerProperties.java \
  /module/spring-boot-tomcat/src/main/java/org/springframework/boot/tomcat/autoconfigure/TomcatVirtualThreadsWebServerFactoryCustomizer.java
clone https://github.com/reactor/reactor-netty v1.3.7 "$SRC/rnetty" /reactor-netty-core/src/main/java/reactor/netty/resources/LoopResources.java
clone https://github.com/reactor/reactor-core v3.8.7 "$SRC/rcore" /reactor-core/src/main/java/reactor/core/scheduler/Schedulers.java
