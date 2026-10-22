#!/usr/bin/env bash
# Registration entries: which workloads get which SPIFFE ID. A pod in namespace "orders" running as service account
# "order-service" receives spiffe://foodfleet.internal/ns/orders/sa/order-service, and nothing else does.
# -dns adds the Kubernetes service name, so ordinary HTTPS clients can verify the server by hostname as well.
set -euo pipefail
TD=spiffe://foodfleet.internal
CLUSTER=foodfleet-prod-ap-south-1
PARENT=$TD/k8s-cluster/$CLUSTER

# a node alias: every agent attested in this cluster (agent IDs end in a node UID, so they can't be listed here)
spire-server entry create -node -spiffeID "$PARENT" -selector "k8s_psat:cluster:$CLUSTER"

entry() {   # namespace serviceAccount
  spire-server entry create \
    -parentID "$PARENT" \
    -spiffeID "$TD/ns/$1/sa/$2" \
    -selector "k8s:ns:$1" \
    -selector "k8s:sa:$2" \
    -dns "$2.$1.svc" \
    -x509SVIDTTL 3600
}

entry orders order-service
entry payments payment-service
entry payments settlement-job
entry riders rider-service
entry catalog catalog-service
