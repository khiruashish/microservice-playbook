#!/usr/bin/env bash
# Builds what chapter 52's experiments run: a minimal Java 25 runtime image (jlink from $JAVA_HOME plus the host's
# glibc: no base image is pulled) and the order-service image on it, loaded into the cluster (k3s or kind); and sparse
# clones of Spring Boot 4.1.1 and Kubernetes 1.37.1 for check_apis.sh. Needs docker and a JDK 21+ with jlink.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C52_SOURCES:-build/src}
JBIN=${JAVA_HOME:+$JAVA_HOME/bin/}
mkdir -p "$SRC" build

clone() {   # dir, repo, tag, sparse paths...
  local dir=$SRC/$1 repo=$2 tag=$3; shift 3
  [ -d "$dir" ] && return
  git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "https://github.com/$repo" "$dir" 2>/dev/null
  (cd "$dir" && git sparse-checkout set --no-cone "$@")
}
clone boot spring-projects/spring-boot v4.1.1 '/module/spring-boot-health/src/main/java/' \
  '/module/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/management/' \
  '/module/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/endpoint/Show.java' \
  '/module/spring-boot-actuator-autoconfigure/src/main/java/org/springframework/boot/actuate/autoconfigure/endpoint/' \
  '/module/spring-boot-actuator-autoconfigure/src/main/java/org/springframework/boot/actuate/autoconfigure/env/' \
  '/module/spring-boot-actuator-autoconfigure/src/main/java/org/springframework/boot/actuate/autoconfigure/web/server/ManagementServerProperties.java' \
  '/module/spring-boot-web-server/src/main/java/org/springframework/boot/web/server/autoconfigure/ServerProperties.java'
clone k8s kubernetes/kubernetes v1.37.1 '/pkg/apis/core/v1/defaults.go' '/pkg/probe/http/http.go' \
  '/staging/src/k8s.io/api/core/v1/types.go' '/pkg/kubelet/prober/worker.go'

# the runtime: jlink'd modules plus the few shared libraries the JVM links against, on an empty image
if ! docker image inspect ff-jre:25 >/dev/null 2>&1; then
  rm -rf build/jre && mkdir -p build/jre/rootfs/{lib/x86_64-linux-gnu,lib64,etc,tmp,opt,app}
  ${JBIN}jlink --add-modules java.base,jdk.httpserver,java.management,jdk.management,jdk.jcmd,jdk.crypto.ec \
    --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output build/jre/rootfs/opt/java
  for l in libc.so.6 libdl.so.2 libm.so.6 libpthread.so.0 librt.so.1 libz.so.1; do
    cp -L "/lib/x86_64-linux-gnu/$l" build/jre/rootfs/lib/x86_64-linux-gnu/
  done
  cp -L /lib64/ld-linux-x86-64.so.2 build/jre/rootfs/lib64/
  printf 'root:x:0:0:root:/:/sbin/nologin\napp:x:10001:10001:app:/app:/sbin/nologin\n' > build/jre/rootfs/etc/passwd
  printf 'root:x:0:\napp:x:10001:\n' > build/jre/rootfs/etc/group
  chmod 1777 build/jre/rootfs/tmp
  printf 'FROM scratch\nCOPY rootfs/ /\nENV PATH=/opt/java/bin JAVA_HOME=/opt/java\nUSER 10001\n' > build/jre/Dockerfile
  docker build -q -t ff-jre:25 build/jre > /dev/null
fi
echo "base image: $(docker image inspect ff-jre:25 --format '{{.Size}}') bytes"
