---
title: "Lab 15 — Kubernetes, Helm & KEDA"
description: "Deploy menu-service with FoodFleet's shared Helm chart on a real single-node k3s: an image built with an AOT cache, pushed and deployed by digest, checked against the platform's 20 rules, then loaded from inside the cluster until the HPA scaled it from 1 pod to 4 (metrics after 42 s, four pods ready 11 s later, utilization settling at 48–49% of requests, no failed requests). kitchen-service gets a KEDA ScaledObject on consumer lag, rendered by the same chart and accepted by KEDA 2.20.2's schema. About 75 minutes."
progress: done
---

# Lab 15 — Kubernetes, Helm & KEDA

<div class="badges" markdown><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **You'll build** | menu-service deployed by digest with the shared chart and scaled by CPU; kitchen-service scaled by Kafka lag with KEDA |
| **Time** | About 75 minutes |
| **Builds on** | [Lab 07](lab-07-kafka.md) (kitchen-service's consumer); [lab 17](lab-17-native-crac.md) goes deeper on start time |
| **Chapters** | [54 (Kubernetes)](../deployment/ch54-kubernetes-for-spring.md), [55 (Helm and kustomize)](../deployment/ch55-helm-kustomize.md), [56 (autoscaling)](../deployment/ch56-autoscaling.md) |
| **Code** | `code/code-lab/lab-15-kubernetes-helm-keda/`, and the chart in `code/foodfleet/deploy/foodfleet-service/` |
| **Needs** | A cluster with metrics-server, Helm 4, Docker, an OCI registry the cluster pulls from, `crane`, JDK 25 |

!!! note "About the lab code"
    This lab **was run** on k3s v1.37.1 (one 2-vCPU node, metrics-server at its default 15 s), Helm 4.1.4, Docker and a local registry; the output below is from that run. KEDA's controller wasn't installed (its images come from a registry this lab couldn't reach): its CRDs from the 2.20.2 release were, so the API server checked the ScaledObject against KEDA's schema. The service is a plain-Java stand-in with the shape the chart expects (ports, probes, graceful shutdown), so the lab runs without Maven.

## 1. One chart for every service

FoodFleet's services share one Helm chart; each service supplies a short values file ([ADR-055](../deployment/ch55-helm-kustomize.md#8-enterprise-case-study)). The chart applies the platform's decisions so teams don't re-derive them: probes on the Actuator port, CPU requests without CPU limits, `ActiveProcessorCount` from the request, a PDB and zone spread, the config checksum on the pod template, an HPA with explicit behaviour, the AOT cache.

```yaml title="values/menu-service.yaml"
--8<-- "code/code-lab/lab-15-kubernetes-helm-keda/values/menu-service.yaml"
```

The image is built with its AOT cache trained in the build, and deployed by digest, never by tag:

```dockerfile title="app/Dockerfile"
--8<-- "code/code-lab/lab-15-kubernetes-helm-keda/app/Dockerfile"
```

The first version of this Dockerfile copied loose `.class` files, and the training run failed: *"Cannot have non-empty directory in paths"*. An AOT cache (like a CDS archive) can only be built for a class path of jars.

## 2. Check before you apply

```console
$ verify/run.sh
== 1. build and push the image; deploy by digest
pushed foodfleet/menu-service@sha256:8d5b9742628702fd92e10ff88e41094dc2252d00a368c35b9e55873a52a92e6c
== 2. render and check against the platform's rules
1 chart(s) linted, 0 chart(s) failed
kitchen-service                          20 of 20
menu-service                             20 of 20
2 of 2 manifests conform
```

`conformance.py` is the same check FoodFleet's CI runs on every rendered service: 20 rules, each naming its ADR. On the lab's first run menu-service failed one, *ADR-060 AOT cache*, because its values had switched the cache off; the fix was to build the image properly, not to waive the rule.

## 3. Scale on CPU

The run installs menu-service with one pod, then a pod inside the cluster sends 25 requests a second through the Service for three minutes. Each request burns about 15 ms of CPU, so the load is about 375 millicores against one pod's 200m request:

```console
== 4. load from inside the cluster: 25 requests/s for 180 s (about 375m of CPU; one pod requests 200m)
  t=10s  cpu % of requests  desired 0  ready 1
  t=21s  cpu % of requests  desired 0  ready 1
  t=32s  cpu % of requests  desired 0  ready 1
  t=42s  cpu 214% of requests  desired 4  ready 1
  t=53s  cpu 197% of requests  desired 4  ready 4
  t=64s  cpu 197% of requests  desired 4  ready 4
  t=75s  cpu 189% of requests  desired 4  ready 4
  t=85s  cpu 49% of requests  desired 4  ready 4
  …
  t=183s  cpu 48% of requests  desired 4  ready 4
  load done: 4500 ok, 0 failed, p50 17 ms, p99 38 ms
```

- **For 42 seconds the HPA saw nothing.** metrics-server scrapes every 15 seconds and needs two samples of a new pod; the HPA checks every 15. That delay is why FoodFleet raises minimums before the lunch and dinner peaks instead of waiting for CPU ([ADR-056](../deployment/ch56-autoscaling.md#8-enterprise-case-study)).
- **Then it went straight to four**: 214% of the request at a 60% target is ceil(1 × 214 / 60) = 4 pods. Not one step at a time.
- **Four pods were ready 11 seconds later**, with a 5-second warm-up before readiness and the AOT cache shortening the start.
- **Utilization settled at 48–49%**: 4 × 200m × 0.49 is about 390m, the load's real cost. Below the 60% target, so no fifth pod.
- **Nothing failed**, and nothing scaled down during the run: the 300-second window means the four pods stay for five minutes after the load stops, in case it comes back.

Chapter 56 measured the same pattern at length: first scale-up 29–34 s after a step, and readiness hidden behind warm-up as the difference between a peak of 5 pods and 8.

## 4. Scale a consumer on its lag: KEDA

CPU is the wrong signal for a Kafka consumer: the backlog grows before CPU does, and a consumer waiting on a slow database uses little CPU while falling behind. ADR-056 scales consumers on **lag**. The shared chart now renders a KEDA `ScaledObject` instead of an HPA when a service sets `autoscaling.kafkaLag`:

```yaml title="values/kitchen-service.yaml"
--8<-- "code/code-lab/lab-15-kubernetes-helm-keda/values/kitchen-service.yaml"
```

```yaml title="foodfleet-service/templates/hpa.yaml (the KEDA branch)"
--8<-- "code/foodfleet/deploy/foodfleet-service/templates/hpa.yaml:22:53"
```

- **KEDA creates the HPA itself**, so the chart renders none: two HPAs on one Deployment overwrite each other's replica count every 15 seconds.
- **`lagThreshold` is a target per replica**: lag 300 means 6 replicas at 50.
- **Replicas beyond the partition count do nothing**: a consumer group gives each partition to one consumer. KEDA caps at the partition count unless `allowIdleConsumers` is set; `order-events` has 24.
- **CPU stays as a second trigger**, at the same 60%, for the case where processing itself is the bottleneck.

The run checks the rendered manifests against KEDA's CRDs on the API server:

```console
== 5. KEDA: kitchen-service's ScaledObject against KEDA's own schema
deployment.apps/kitchen-service created (server dry run)
scaledobject.keda.sh/kitchen-service created (server dry run)
```

The schema checks the structure. Trigger metadata is free-form there, so a misspelt key like `lagTreshold` passes the dry run and fails only in KEDA's admission webhook or operator; the key names here were checked in KEDA 2.20.2's Kafka scaler source. The conformance rule for ADR-056 accepts either an HPA or this ScaledObject, and the change to the chart left the other 21 services' rendered manifests byte-for-byte the same.

## Run it

```bash
K3S_DISABLE=traefik code/chapters/ch52-health-checks-actuator/verify/k3s-lab.sh   # k3s with metrics-server
code/chapters/ch55-helm-kustomize/verify/registry-lab.sh                          # a registry k3s pulls from
code/chapters/ch54-kubernetes-for-spring/verify/build-libs.sh                     # the c54/jre:25 base image
curl -sSfLO https://github.com/kedacore/keda/releases/download/v2.20.2/keda-2.20.2-crds.yaml
KUBECONFIG=/etc/rancher/k3s/k3s.yaml KUBECTL="k3s kubectl" HELM=helm CRANE=crane \
  KEDA_CRDS=$PWD/keda-2.20.2-crds.yaml code/code-lab/lab-15-kubernetes-helm-keda/verify/run.sh
```

## Check yourself

- [ ] `run.sh` shows 20 of 20 for both services, and the HPA reaching 4 pods with no failed requests.
- [ ] You can compute the HPA's choice of 4 from the utilization it reported.
- [ ] You can explain why kitchen-service's maximum shouldn't exceed `order-events`' partition count.

## Break it

!!! question "Exercises"
    1. Set `WARMUP_SECONDS=0` and burn a CPU for the first 10 seconds of each pod's life (a JIT warming up). How many pods does the HPA reach now? (Chapter 56: 8 instead of 5.)
    2. Render kitchen-service with `kafkaLag` and also force the chart's HPA on. Apply both on a cluster with KEDA. What do the two controllers do to `replicas`?
    3. Deploy by tag instead of digest, push a new image to the same tag, and delete one pod. Which version is each pod running?
    4. Stop the load after two minutes and keep watching. When does the first pod go, and when is it back to one?

**Next:** [Lab 16](lab-16-istio-canary.md) adds mTLS and a canary release with Istio and Argo Rollouts.
