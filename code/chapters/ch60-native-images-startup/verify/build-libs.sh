#!/usr/bin/env bash
# Builds menu-service five ways for chapter 60's experiments, with no base image pulled:
#   c60/jre:25   a jlink'd Java 25 runtime (with its default CDS archive) plus the host's glibc
#   c60/glibc    glibc only, for the native executable
#   foodfleet/menu-service:ch60-jvm, -cds (AppCDS archive), -aot (JDK 25 AOT cache), -aot-core (the same, from a
#   training run without HTTP), -native (GraalVM native image)
# and sparse clones of the JDK, GraalVM, CRaC and Spring Framework sources for check_apis.sh.
# Needs docker, git, a JDK 25 (JAVA_HOME) and GraalVM CE 25 (GRAALVM_HOME). The native build takes about 3 minutes
# and 1.7 GB of memory.
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
: "${JAVA_HOME:?set JAVA_HOME to a JDK 25}" "${GRAALVM_HOME:?set GRAALVM_HOME to GraalVM CE 25}"
SRC=${C60_SOURCES:-build/src}
B=build
mkdir -p "$SRC" $B

clone() {   # repo, tag, dir, paths...
  local repo=$1 tag=$2 dir=$3; shift 3
  if [ ! -d "$dir" ]; then
    git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "$repo" "$dir" 2>/dev/null
    (cd "$dir" && git sparse-checkout set --no-cone "$@")
  fi
}
clone https://github.com/openjdk/jdk25u jdk-25.0.4+7 "$SRC/jdk" /src/hotspot/share/cds/ /src/hotspot/share/runtime/globals.hpp \
  /src/hotspot/share/runtime/arguments.cpp
clone https://github.com/oracle/graal vm-25.0.2 "$SRC/graal" /substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/SubstrateGCOptions.java \
  /substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/SubstrateOptions.java /substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/heap/ \
  /substratevm/src/com.oracle.svm.core.genscavenge/src/com/oracle/svm/core/genscavenge/HeapParameters.java \
  /substratevm/src/com.oracle.svm.core.genscavenge/src/com/oracle/svm/core/genscavenge/SerialAndEpsilonGCOptions.java
clone https://github.com/openjdk/crac crac-24+20 "$SRC/crac" /src/hotspot/share/runtime/globals.hpp /src/java.base/share/classes/jdk/crac/ \
  /src/java.base/share/classes/jdk/internal/crac/mirror/Core.java
clone https://github.com/spring-projects/spring-framework v7.0.9 "$SRC/sf" \
  /spring-context/src/main/java/org/springframework/context/support/DefaultLifecycleProcessor.java

# menu-service: chapter 53's libraries (Micrometer, Prometheus client, SLF4J, Logback), or the jars in $C60_LIBS
rm -rf $B/classes $B/app $B/tools && mkdir -p $B/classes $B/app $B/tools
if [ -n "${C60_LIBS:-}" ]; then
  cp "$C60_LIBS"/*.jar $B/app/
else
  ../../ch53-containerization/verify/build-libs.sh > /dev/null
  for d in ../../ch53-containerization/verify/build/lib/deps/*; do "$JAVA_HOME/bin/jar" --create --file "$B/app/$(basename "$d").jar" -C "$d" .; done
fi
"$JAVA_HOME/bin/javac" --release 25 -nowarn -XDsuppressNotes -d $B/classes -cp "$B/app/*" $(find ../src/main/java -name '*.java') tools/StartupLab.java tools/Trainer.java
cp ../src/main/resources/logback.xml $B/classes/
"$JAVA_HOME/bin/jar" --create --file $B/app/menu-service.jar -C $B/classes .
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d $B/tools tools/Measure.java

rootfs() {   # dir: glibc, an app user and /tmp
  rm -rf "$1" && mkdir -p "$1"/{lib/x86_64-linux-gnu,lib64,etc,tmp,opt}
  for l in libc.so.6 libdl.so.2 libm.so.6 libpthread.so.0 librt.so.1 libz.so.1; do cp -L "/lib/x86_64-linux-gnu/$l" "$1/lib/x86_64-linux-gnu/"; done
  cp -L /lib64/ld-linux-x86-64.so.2 "$1/lib64/"
  printf 'root:x:0:0:root:/:/sbin/nologin\napp:x:10001:10001:app:/app:/sbin/nologin\n' > "$1/etc/passwd"
  printf 'root:x:0:\napp:x:10001:\n' > "$1/etc/group"
  chmod 1777 "$1/tmp"
}
rootfs $B/glibc/rootfs
printf 'FROM scratch\nCOPY rootfs/ /\nUSER 10001\n' > $B/glibc/Dockerfile
docker build -q -t c60/glibc $B/glibc > /dev/null
rootfs $B/jre/rootfs
# jdk.jcmd and jdk.attach for jcmd; jlink's --generate-cds-archive gives the runtime its default CDS archive
"$JAVA_HOME/bin/jlink" --add-modules java.base,java.logging,java.management,java.net.http,java.sql,jdk.httpserver,jdk.management,jdk.crypto.ec,jdk.jcmd \
  --strip-debug --no-header-files --no-man-pages --compress=zip-6 --generate-cds-archive --output $B/jre/rootfs/opt/java
printf 'FROM scratch\nCOPY rootfs/ /\nENV PATH=/opt/java/bin JAVA_HOME=/opt/java\nUSER 10001\n' > $B/jre/Dockerfile
docker build -q -t c60/jre:25 $B/jre > /dev/null

# the three JVM images: the training runs for CDS and AOT happen inside docker build
for v in jvm cds aot aot-core; do
  rm -rf $B/ctx-$v && mkdir -p $B/ctx-$v && cp -r $B/app $B/ctx-$v/ && cp ../src/main/resources/docker/Dockerfile.$v $B/ctx-$v/Dockerfile
  s=$(date +%s%N); docker build -q -t foodfleet/menu-service:ch60-$v $B/ctx-$v > /dev/null
  echo "image ch60-$v built in $(( ($(date +%s%N) - s) / 1000000 )) ms"
done

# native: record reflection, resources and service loaders with the agent during the same training run, then
# compile ahead of time
rm -rf $B/ni-config $B/ctx-native && mkdir -p $B/ctx-native
"$GRAALVM_HOME/bin/java" -agentlib:native-image-agent=config-output-dir=$B/ni-config -cp "$B/app/*" Trainer > $B/agent.log 2>&1
s=$(date +%s)
"$GRAALVM_HOME/bin/native-image" -cp "$B/app/*" -H:ConfigurationFileDirectories=$B/ni-config --no-fallback -o $B/ctx-native/menu-service StartupLab > $B/native.log 2>&1
echo "native executable built in $(( $(date +%s) - s )) s: $(grep -E 'Peak RSS' $B/native.log | sed 's/^ *//')"
cp ../src/main/resources/docker/Dockerfile.native $B/ctx-native/Dockerfile
docker build -q -t foodfleet/menu-service:ch60-native $B/ctx-native > /dev/null
docker image ls 'foodfleet/menu-service' --format '{{.Tag}} {{.Size}}' | grep ch60
