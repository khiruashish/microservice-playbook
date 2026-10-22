#!/usr/bin/env bash
# Builds chapter 59's identity-lab image (on chapter 54's jlink'd Java 25 runtime; no base image is pulled) and loads
# it into the cluster, makes sure chapter 54's menu-service image exists for the cold-start measurement, and clones
# the AWS SDK for Java and Kubernetes sources for check_apis.sh. Needs docker, git and a JDK 25.
set -euo pipefail
cd "$(dirname "$0")"
unset JAVA_TOOL_OPTIONS
SRC=${C59_SOURCES:-build/src}
LOAD_IMAGE=${LOAD_IMAGE:-../../ch52-health-checks-actuator/verify/build/k3s/k3s ctr images import -}
mkdir -p "$SRC"
clone() {   # repo, tag, dir, paths...
  local repo=$1 tag=$2 dir=$3; shift 3
  if [ ! -d "$dir" ]; then
    git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "$repo" "$dir" 2>/dev/null
    (cd "$dir" && git sparse-checkout set --no-cone "$@")
  fi
}
clone https://github.com/aws/aws-sdk-java-v2 2.56.0 "$SRC/aws" /core/retries/src/main/java/ /core/retries-spi/src/main/java/ \
  /core/sdk-core/src/main/java/software/amazon/awssdk/core/SdkSystemSetting.java
clone https://github.com/kubernetes/kubernetes v1.37.1 "$SRC/k8s" /pkg/serviceaccount/claims.go /pkg/kubelet/token/token_manager.go \
  /pkg/apis/core/validation/validation.go /pkg/kubeapiserver/options/authentication.go
docker image inspect foodfleet/menu-service:ch54 > /dev/null 2>&1 || ../../ch54-kubernetes-for-spring/verify/run.sh cpu > /dev/null
../../ch54-kubernetes-for-spring/verify/build-libs.sh > /dev/null      # the c54/jre:25 runtime image
rm -rf build/main build/tools build/image && mkdir -p build/main build/tools build/image/app
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/main $(find ../src/main/java -name '*.java')
"$JAVA_HOME/bin/javac" --release 25 -nowarn -d build/tools -cp build/main tools/*.java
cp build/tools/IdentityLab.class build/image/app/
printf 'FROM c54/jre:25\nCOPY app/ /app/\nENTRYPOINT ["java", "-cp", "/app", "IdentityLab"]\n' > build/image/Dockerfile
docker build -q -t foodfleet/identity:ch59 build/image > /dev/null
if [[ "$LOAD_IMAGE" == *" -" ]]; then docker save foodfleet/identity:ch59 | ${LOAD_IMAGE} > /dev/null 2>&1
else ${LOAD_IMAGE} foodfleet/identity:ch59 > /dev/null; fi
