#!/usr/bin/env bash
# Prepares chapter 72: JUnit 6 (chapter 61's build-libs.sh), chapter 54's c54/jre:25 runtime image (the template's base
# image), and sparse clones of Backstage, Copier, git, VPA and Kubernetes for check_apis.sh. Needs docker and git.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C72_SOURCES:-build/src}
mkdir -p "$SRC" build
C61_SOURCES="$(cd ../../ch61-test-strategy/verify && pwd)/build/src" ../../ch61-test-strategy/verify/build-libs.sh > /dev/null
ln -sfn "$(cd ../../ch61-test-strategy/verify/build/lib && pwd)" build/lib
../../ch54-kubernetes-for-spring/verify/build-libs.sh > /dev/null
clone() {   # repo, tag, dir, paths...
  local repo=$1 tag=$2 dir=$3; shift 3
  if [ ! -d "$dir" ]; then
    git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "$repo" "$dir" 2>/dev/null
    (cd "$dir" && git sparse-checkout set --no-cone "$@")
  fi
}
clone https://github.com/backstage/backstage v1.55.3 "$SRC/backstage" /plugins/scaffolder-common/src/Template.v1beta3.schema.json \
  /plugins/scaffolder-backend/src/scaffolder/actions/builtin/fetch/template.ts
clone https://github.com/copier-org/copier v9.19.1 "$SRC/copier" /copier/_main.py
clone https://github.com/git/git v2.56.0 "$SRC/git" /Documentation/git-merge-file.adoc
clone https://github.com/kubernetes/autoscaler vertical-pod-autoscaler/v1.8.0 "$SRC/vpa" /vertical-pod-autoscaler/pkg/apis/autoscaling.k8s.io/v1/types.go
clone https://github.com/kubernetes/kubernetes v1.37.1 "$SRC/k8s" /staging/src/k8s.io/apimachinery/pkg/apis/meta/v1/types.go
