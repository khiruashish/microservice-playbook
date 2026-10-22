#!/usr/bin/env bash
# A local OCI registry (distribution 3.1.2 from chapter 53's build) as registry.foodfleet.local:5000, and k3s told to
# pull from it over plain HTTP. For the k3s cluster of ../../ch52-health-checks-actuator/verify/k3s-lab.sh; needs root.
# With your own cluster and registry, skip this and set REGISTRY (and CRANE_FLAGS if it speaks TLS).
set -euo pipefail
cd "$(dirname "$0")"
BIN=../../ch53-containerization/verify/build/bin
mkdir -p build/registry
grep -q 'registry.foodfleet.local' /etc/hosts || echo '127.0.0.1 registry.foodfleet.local' >> /etc/hosts
printf 'version: 0.1\nstorage: {filesystem: {rootdirectory: %s}, delete: {enabled: true}}\nhttp: {addr: 0.0.0.0:5000}\nlog: {level: error}\n' \
  "$PWD/build/registry/data" > build/registry/config.yml
pgrep -x registry > /dev/null || setsid -f "$BIN/registry" serve build/registry/config.yml > build/registry/log 2>&1 < /dev/null
mkdir -p /etc/rancher/k3s
if [ ! -f /etc/rancher/k3s/registries.yaml ]; then
  printf 'mirrors:\n  "registry.foodfleet.local:5000":\n    endpoint: ["http://127.0.0.1:5000"]\n' > /etc/rancher/k3s/registries.yaml
  echo "wrote /etc/rancher/k3s/registries.yaml: restart k3s to pick it up"
fi
