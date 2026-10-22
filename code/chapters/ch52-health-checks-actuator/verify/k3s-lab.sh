#!/usr/bin/env bash
# Starts a single-node Kubernetes (k3s v1.37.1+k3s1, from its GitHub release, with the air-gapped system images) for
# the chapter 52-54 experiments. Needs root and Linux x86-64. Already have a cluster? Skip this and set KUBECTL.
#   ./k3s-lab.sh            start (idempotent)
# K3S_DISABLE lists the bundled components to leave out (default traefik,metrics-server). Chapter 56 needs
# metrics-server (K3S_DISABLE=traefik); chapter 58 needs Traefik too (K3S_DISABLE=).
# K3S_SANDBOX=1 adds two workarounds for restricted VMs (the handbook's lab ran in one): kubelet allowed on cgroup v1,
# and negative oom_score_adj values clamped to 0 where CAP_SYS_RESOURCE is missing. Don't use them on a real host.
set -euo pipefail
cd "$(dirname "$0")"
V=v1.37.1%2Bk3s1
BIN=${K3S_HOME:-build/k3s}
mkdir -p "$BIN"
[ -x "$BIN/k3s" ] || { curl -sSfL -o "$BIN/k3s" "https://github.com/k3s-io/k3s/releases/download/$V/k3s"; chmod +x "$BIN/k3s"; }
mkdir -p /var/lib/rancher/k3s/agent/images
[ -s /var/lib/rancher/k3s/agent/images/k3s-airgap-images-amd64.tar.zst ] || \
  curl -sSfL -o /var/lib/rancher/k3s/agent/images/k3s-airgap-images-amd64.tar.zst \
    "https://github.com/k3s-io/k3s/releases/download/$V/k3s-airgap-images-amd64.tar.zst"
if "$BIN/k3s" kubectl get nodes >/dev/null 2>&1; then echo "k3s already running"; exit 0; fi
DISABLE=${K3S_DISABLE-traefik,metrics-server}
ARGS=(server ${DISABLE:+"--disable=$DISABLE"} --write-kubeconfig-mode=644 --snapshotter=native
      '--kubelet-arg=eviction-hard=imagefs.available<1%,nodefs.available<1%,memory.available<100Mi')
if [ "${K3S_SANDBOX:-0}" = 1 ]; then
  ARGS+=(--kubelet-arg=fail-cgroupv1=false)
fi
env -u HTTPS_PROXY -u HTTP_PROXY -u https_proxy -u http_proxy setsid -f "$BIN/k3s" "${ARGS[@]}" > build/k3s.log 2>&1 < /dev/null
for i in $(seq 1 120); do "$BIN/k3s" kubectl get nodes 2>/dev/null | grep -q ' Ready' && break; sleep 1; done
if [ "${K3S_SANDBOX:-0}" = 1 ]; then
  R=/var/lib/rancher/k3s/data/current/bin
  if [ ! -f "$R/runc.real" ]; then
    mv "$R/runc" "$R/runc.real"
    cat > "$R/runc" <<'SH'
#!/bin/bash
# sandbox only: no CAP_SYS_RESOURCE, so negative oom_score_adj values in the OCI bundle are clamped to 0
b=""; p=""
for a in "$@"; do { [ "$p" = --bundle ] || [ "$p" = -b ]; } && b="$a"; case "$a" in --bundle=*) b="${a#--bundle=}";; esac; p="$a"; done
[ -n "$b" ] && [ -f "$b/config.json" ] && /usr/bin/python3 -I -c 'import json,sys;p=sys.argv[1];c=json.load(open(p));q=c.get("process",{})
if q.get("oomScoreAdj",0)<0: q["oomScoreAdj"]=0; json.dump(c,open(p,"w"))' "$b/config.json"
exec "$(dirname "$0")/runc.real" "$@"
SH
    chmod +x "$R/runc"
  fi
fi
"$BIN/k3s" kubectl get nodes
