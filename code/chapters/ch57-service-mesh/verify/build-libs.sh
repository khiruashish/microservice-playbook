#!/usr/bin/env bash
# Builds chapter 57's image: Envoy 1.39.4 (from Envoy's GitHub release) and the MeshLab classes on chapter 54's jlink'd
# Java 25 runtime (Envoy needs only glibc, which that image has; no base image is pulled), loads it into the cluster,
# validates every sidecar configuration with Envoy itself, makes the mesh's certificates, and clones Envoy's and
# Kubernetes' sources for check_apis.sh. Needs docker, git, curl, openssl and a JDK 25.
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
SRC=${C57_SOURCES:-build/src}
LOAD_IMAGE=${LOAD_IMAGE:-../../ch52-health-checks-actuator/verify/build/k3s/k3s ctr images import -}
mkdir -p "$SRC" build/bin
clone() {   # repo, tag, dir, paths...
  local repo=$1 tag=$2 dir=$3; shift 3
  if [ ! -d "$dir" ]; then
    git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "$repo" "$dir" 2>/dev/null
    (cd "$dir" && git sparse-checkout set --no-cone "$@")
  fi
}
clone https://github.com/envoyproxy/envoy v1.39.4 "$SRC/envoy" /api/envoy/config/cluster/v3/outlier_detection.proto \
  /api/envoy/config/route/v3/route_components.proto /api/envoy/config/cluster/v3/cluster.proto \
  /api/envoy/extensions/transport_sockets/tls/v3/tls.proto /api/envoy/extensions/transport_sockets/tls/v3/common.proto
clone https://github.com/kubernetes/kubernetes v1.37.1 "$SRC/k8s" /staging/src/k8s.io/api/core/v1/types.go
[ -x build/bin/envoy ] || { curl -sSfL -o build/bin/envoy https://github.com/envoyproxy/envoy/releases/download/v1.39.4/envoy-1.39.4-linux-x86_64; chmod +x build/bin/envoy; }
[ -f build/certs/ca.crt ] || ./gen-certs.sh build/certs mesh
../../ch54-kubernetes-for-spring/verify/build-libs.sh > /dev/null      # the c54/jre:25 runtime image
rm -rf build/main build/tools build/image && mkdir -p build/main build/tools build/image/app build/image/bin
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/main $(find ../src/main/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp build/main tools/*.java
cp build/tools/MeshLab*.class build/image/app/ && cp build/bin/envoy build/image/bin/
printf 'FROM c54/jre:25\nCOPY bin/envoy /usr/local/bin/envoy\nCOPY app/ /app/\nENV PATH=/opt/java/bin:/usr/local/bin\n' > build/image/Dockerfile
docker build -q -t foodfleet/mesh:ch57 build/image > /dev/null
if [[ "$LOAD_IMAGE" == *" -" ]]; then docker save foodfleet/mesh:ch57 | ${LOAD_IMAGE} > /dev/null 2>&1
else ${LOAD_IMAGE} foodfleet/mesh:ch57 > /dev/null; fi
