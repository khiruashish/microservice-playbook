#!/usr/bin/env bash
# Lab 15 on a real single-node k3s: render and check both services with the shared chart, install menu-service,
# load it until the HPA scales out, and validate kitchen-service's KEDA ScaledObject against KEDA's CRDs.
# Needs: KUBECTL (a cluster with metrics-server), HELM (4.x), a local registry REGISTRY_PUSH (reachable from here)
# that the cluster pulls as REGISTRY_PULL, docker and crane, a JDK 25, curl. About 6 minutes.
set -euo pipefail
unset JAVA_TOOL_OPTIONS
cd "$(dirname "$0")"
: "${KUBECTL:?}" "${HELM:?}" "${CRANE:?}"
REGISTRY_PUSH=${REGISTRY_PUSH:-127.0.0.1:5000}
REGISTRY_PULL=${REGISTRY_PULL:-registry.foodfleet.local:5000}
J=${JAVA_HOME:+$JAVA_HOME/bin/}
CHART=../../../foodfleet/deploy/foodfleet-service
W=build; rm -rf $W && mkdir -p $W/image
k() { $KUBECTL "$@"; }

echo "== 1. build and push the image; deploy by digest"
${J}javac --release 25 -d $W/classes ../app/MenuStandIn.java ../app/LoadGen.java
${J}jar --create --file $W/image/menu-service.jar -C $W/classes .
cp ../app/Dockerfile $W/image/
docker build -q -t lab15/menu-service:build $W/image > /dev/null
docker save lab15/menu-service:build -o $W/image.tar
$CRANE push --insecure $W/image.tar $REGISTRY_PUSH/foodfleet/menu-service:lab15 > /dev/null
DIGEST=$($CRANE digest --insecure $REGISTRY_PUSH/foodfleet/menu-service:lab15)
echo "pushed foodfleet/menu-service@$DIGEST"

echo "== 2. render and check against the platform's rules"
$HELM lint $CHART -f ../values/menu-service.yaml --set image.digest=$DIGEST | tail -1
$HELM template menu-service $CHART -f ../values/menu-service.yaml \
  --set image.repository=$REGISTRY_PULL/foodfleet --set image.digest=$DIGEST > $W/menu-service.yaml
$HELM template kitchen-service $CHART -f ../values/kitchen-service.yaml > $W/kitchen-service.yaml
python3 ../../../foodfleet/deploy/conformance.py $W | tail -3
grep -h "^kind:" $W/kitchen-service.yaml | sort | uniq -c | sed 's/^/kitchen-service renders: /'

echo "== 3. install menu-service and wait for it"
$HELM uninstall menu-service -n menu-service --wait > /dev/null 2>&1 || true      # start from one pod
k create namespace menu-service --dry-run=client -o yaml | k apply -f - > /dev/null
$HELM upgrade --install menu-service $CHART -n menu-service -f ../values/menu-service.yaml \
  --set image.repository=$REGISTRY_PULL/foodfleet --set image.digest=$DIGEST --wait --timeout 3m > /dev/null
k -n menu-service get deploy,hpa,pdb -o wide | sed 's/^/  /'

echo "== 4. load from inside the cluster: 25 requests/s for 180 s (about 375m of CPU; one pod requests 200m)"
k -n menu-service delete pod load --ignore-not-found --wait=true > /dev/null
k -n menu-service run load --restart=Never --image=$REGISTRY_PULL/foodfleet/menu-service@$DIGEST \
  --command -- java -cp /app/menu-service.jar LoadGen http://menu-service.menu-service.svc/menus/r-1 25 180 > /dev/null
T0=$(date +%s)
until k -n menu-service get pod load -o jsonpath='{.status.phase}' | grep -qE 'Succeeded|Failed'; do
  sleep 10
  echo "  t=$(( $(date +%s) - T0 ))s  cpu $(k -n menu-service get hpa menu-service -o jsonpath='{.status.currentMetrics[0].resource.current.averageUtilization}')% of requests" \
       " desired $(k -n menu-service get hpa menu-service -o jsonpath='{.status.desiredReplicas}')" \
       " ready $(k -n menu-service get deploy menu-service -o jsonpath='{.status.readyReplicas}')"
done
k -n menu-service logs load
k -n menu-service delete pod load --wait=false > /dev/null

echo "== 5. KEDA: kitchen-service's ScaledObject against KEDA's own schema"
k apply --server-side -f "${KEDA_CRDS:?set KEDA_CRDS to keda-2.20.2-crds.yaml}" > /dev/null
k create namespace kitchen-service --dry-run=client -o yaml | k apply -f - > /dev/null
k apply --dry-run=server -n kitchen-service -f $W/kitchen-service.yaml
