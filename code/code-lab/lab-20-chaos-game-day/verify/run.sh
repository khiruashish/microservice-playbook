#!/usr/bin/env bash
# Lab 20's checks before a game day:
#   1. paging.py       which planned faults will page, and when, by FoodFleet's real SLO rules (~4 minutes)
#   2. experiments.sh  the rehearsal on a laptop: three clients against slow and hung dependencies (~6 minutes)
#   3. the game day's Chaos Mesh workflow against Chaos Mesh 2.8.4's CRDs, if KUBECTL and CHAOS_MESH_CRDS are set
# Needs PROMTOOL, python3 with PyYAML, a JDK 25.
set -euo pipefail
cd "$(dirname "$0")"
echo "== 1. who gets paged"
python3 paging.py "${PROMTOOL:?}"
echo "== 2. rehearsal"
./experiments.sh
if [ -n "${KUBECTL:-}" ]; then
  echo "== 3. the workflow"
  $KUBECTL apply --server-side -f "${CHAOS_MESH_CRDS:?}" > /dev/null
  $KUBECTL create namespace foodfleet --dry-run=client -o yaml | $KUBECTL apply -f - > /dev/null
  $KUBECTL apply --dry-run=server -f ../gameday/checkout-gameday.yaml
fi
