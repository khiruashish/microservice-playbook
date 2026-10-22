#!/usr/bin/env bash
# Builds what chapter 55's experiments need: kustomize 5.8.3 (GitHub release) and Helm 4.1.4 (get.helm.sh) unless
# HELM / KUSTOMIZE point at your own; the registry and crane from chapter 53's build; two menu-service images, 1.4.0
# and 1.4.1, on chapter 54's jlink'd runtime (no base image is pulled); and sparse clones of Helm and kustomize for
# check_apis.sh. Needs docker, git, curl and a JDK 25.
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
SRC=${C55_SOURCES:-build/src}
mkdir -p "$SRC" build/bin
GH=https://github.com
fetch() { [ -s "$2" ] || curl -sSfL -o "$2" "$1"; }
if [ -z "${KUSTOMIZE:-}" ] && [ ! -x build/bin/kustomize ]; then
  fetch "$GH/kubernetes-sigs/kustomize/releases/download/kustomize%2Fv5.8.3/kustomize_v5.8.3_linux_amd64.tar.gz" build/bin/kustomize.tgz
  (cd build/bin && tar xzf kustomize.tgz kustomize)
fi
if [ -z "${HELM:-}" ] && [ ! -x build/bin/helm ]; then
  fetch https://get.helm.sh/helm-v4.1.4-linux-amd64.tar.gz build/bin/helm.tgz
  (cd build/bin && tar xzf helm.tgz --strip-components=1 linux-amd64/helm)
fi
clone() {   # repo, tag, dir, paths...
  local repo=$1 tag=$2 dir=$3; shift 3
  if [ ! -d "$dir" ]; then
    git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "$repo" "$dir" 2>/dev/null
    (cd "$dir" && git sparse-checkout set --no-cone "$@")
  fi
}
clone $GH/helm/helm v4.1.4 "$SRC/helm" /pkg/cmd/install.go /pkg/cmd/upgrade.go /pkg/action/upgrade.go /pkg/kube/client.go \
  /pkg/action/install.go
clone $GH/kubernetes-sigs/kustomize kustomize/v5.8.3 "$SRC/kustomize" /api/types/generatoroptions.go /api/hasher/hasher.go \
  /api/filters/imagetag/updater.go
# the registry and crane, and the libraries menu-service is built with, as chapter 53 builds them
../../ch53-containerization/verify/build-libs.sh > /dev/null
../../ch54-kubernetes-for-spring/verify/build-libs.sh > /dev/null      # the c54/jre:25 runtime image
rm -rf build/tools build/image && mkdir -p build/tools
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools tools/VersionedService.java
for v in 1.4.0 1.4.1; do
  mkdir -p "build/image/$v/app" && cp build/tools/VersionedService.class "build/image/$v/app/" && printf '%s\n' "$v" > "build/image/$v/app/version.txt"
  printf 'FROM c54/jre:25\nCOPY app/ /app/\nENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-cp", "/app", "VersionedService"]\n' > "build/image/$v/Dockerfile"
  docker build -q -t "foodfleet/menu-service:$v" "build/image/$v" > /dev/null
done
