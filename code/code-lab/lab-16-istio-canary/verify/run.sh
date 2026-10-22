#!/usr/bin/env bash
# Lab 16's checks, none of which needs Istio or Argo Rollouts running:
#   1. istioctl validate and analyze (offline) on the mesh manifests
#   2. the canary analysis queries, against synthetic Istio metrics, with promtool's unit tests
#   3. with a cluster (KUBECTL set): Istio's and Argo Rollouts' CRDs installed, then every manifest dry-run on the
#      API server, so each field is checked against the real schemas
# Needs ISTIOCTL (1.31.1), PROMTOOL (3.x), python3 with PyYAML; for 3: KUBECTL, HELM, ISTIO_CRDS
# (istio-1.31.1/manifests/charts/base/files/crd-all.gen.yaml) and ROLLOUTS_INSTALL (argo-rollouts v1.9.2 install.yaml).
set -euo pipefail
cd "$(dirname "$0")"
echo "== 1. istioctl $($ISTIOCTL version --remote=false 2>/dev/null | awk '{print $NF}')"
$ISTIOCTL validate -f ../istio/mesh-security.yaml -f ../istio/traffic.yaml
$ISTIOCTL analyze --use-kube=false ../istio/mesh-security.yaml ../istio/traffic.yaml

echo "== 2. canary analysis queries"
python3 test_analysis.py "$PROMTOOL"

[ -n "${KUBECTL:-}" ] || exit 0
echo "== 3. dry run against the API server's schemas"
python3 - "$ROLLOUTS_INSTALL" > build-rollouts-crds.yaml <<'PY'
import sys, yaml
docs = [d for d in yaml.safe_load_all(open(sys.argv[1])) if d and d.get("kind") == "CustomResourceDefinition"]
print(yaml.safe_dump_all(docs))
PY
$KUBECTL apply --server-side -f "$ISTIO_CRDS" > /dev/null
$KUBECTL apply --server-side -f build-rollouts-crds.yaml > /dev/null
rm build-rollouts-crds.yaml
$KUBECTL wait --for=condition=Established crd/virtualservices.networking.istio.io crd/rollouts.argoproj.io \
  crd/analysistemplates.argoproj.io crd/authorizationpolicies.security.istio.io --timeout=60s > /dev/null
$KUBECTL create namespace menu-service --dry-run=client -o yaml | $KUBECTL apply -f - > /dev/null
$HELM template menu-service ../../../foodfleet/deploy/foodfleet-service -f ../../lab-15-kubernetes-helm-keda/values/menu-service.yaml \
  --set autoscaling.target=Rollout \
  --set image.digest=sha256:0000000000000000000000000000000000000000000000000000000000000000 > build-chart.yaml
for f in build-chart.yaml ../istio/mesh-security.yaml ../istio/traffic.yaml ../rollouts/rollout.yaml ../rollouts/analysis.yaml; do
  $KUBECTL apply --dry-run=server -n menu-service -f "$f" 2>&1 | grep -v "^Warning: resource" | sed "s|^|  |"
done
rm build-chart.yaml
