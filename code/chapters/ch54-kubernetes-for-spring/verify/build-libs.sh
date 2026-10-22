#!/usr/bin/env bash
# Builds what chapter 54's experiments run: menu-service's image (the libraries from chapter 53's build, a jlink'd
# Java 25 runtime from $JAVA_HOME plus the host's glibc; no base image is pulled), loaded into the cluster; and a
# sparse clone of Kubernetes 1.37.1 for check_apis.sh. Needs docker and a JDK 25.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C54_SOURCES:-build/src}
mkdir -p "$SRC" build
if [ ! -d "$SRC/k8s" ]; then
  git clone -q --depth 1 --filter=blob:none --sparse --branch v1.37.1 https://github.com/kubernetes/kubernetes "$SRC/k8s" 2>/dev/null
  (cd "$SRC/k8s" && git sparse-checkout set --no-cone /pkg/volume/util/atomic_writer.go /pkg/registry/core/pod/storage/eviction.go \
    /pkg/kubelet/cm/helpers_linux.go /pkg/kubelet/apis/config/v1beta1/defaults.go /staging/src/k8s.io/api/core/v1/types.go \
    /pkg/kubelet/kubelet_resources.go)
fi
# menu-service's libraries, as chapter 53 builds them (Micrometer, Prometheus client, SLF4J, Logback)
../../ch53-containerization/verify/build-libs.sh > /dev/null
if ! docker image inspect c54/jre:25 >/dev/null 2>&1; then
  rm -rf build/jre && mkdir -p build/jre/rootfs/{lib/x86_64-linux-gnu,lib64,etc,tmp,opt}
  "$JAVA_HOME/bin/jlink" --add-modules java.base,java.net.http,java.sql,jdk.httpserver,jdk.management,jdk.crypto.ec,jdk.jcmd \
    --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output build/jre/rootfs/opt/java
  for l in libc.so.6 libdl.so.2 libm.so.6 libpthread.so.0 librt.so.1 libz.so.1; do cp -L "/lib/x86_64-linux-gnu/$l" build/jre/rootfs/lib/x86_64-linux-gnu/; done
  cp -L /lib64/ld-linux-x86-64.so.2 build/jre/rootfs/lib64/
  printf 'root:x:0:0:root:/:/sbin/nologin\napp:x:10001:10001:app:/app:/sbin/nologin\n' > build/jre/rootfs/etc/passwd
  printf 'root:x:0:\napp:x:10001:\n' > build/jre/rootfs/etc/group
  chmod 1777 build/jre/rootfs/tmp
  printf 'FROM scratch\nCOPY rootfs/ /\nENV PATH=/opt/java/bin JAVA_HOME=/opt/java\nUSER 10001\n' > build/jre/Dockerfile
  docker build -q -t c54/jre:25 build/jre > /dev/null
fi
