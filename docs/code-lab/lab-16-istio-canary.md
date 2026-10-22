---
title: "Lab 16 — Istio mTLS & Canary"
description: "Put menu-service in the mesh with mutual TLS required and only the platform model's callers allowed, then release it as a canary with Argo Rollouts: 10, 25, 50% of traffic through an Istio VirtualService, the canary's pods following its share, and an analysis that promotes, waits or aborts on error rate and p99 relative to stable. The analysis queries were tested with promtool against four synthetic canaries, which caught a query that would have left every error-free canary inconclusive; every manifest passed istioctl and a dry run against Istio 1.31.1's and Argo Rollouts 1.9.2's schemas. About an hour."
progress: done
---

# Lab 16 — Istio mTLS & Canary

<div class="badges" markdown><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **You'll build** | menu-service's mesh security (mTLS, authorization by identity), its traffic routing, and a canary release that analyses itself |
| **Time** | About an hour |
| **Builds on** | [Lab 15](lab-15-kubernetes-helm-keda.md) (the shared chart) and [lab 12](lab-12-observability.md) (metrics) |
| **Chapters** | [57 (service mesh)](../deployment/ch57-service-mesh.md), [58 (CI/CD, GitOps, progressive delivery)](../deployment/ch58-cicd-gitops.md), [38 (service-to-service security)](../security/ch38-service-to-service-security.md) |
| **Code** | `code/code-lab/lab-16-istio-canary/` |
| **Needs** | `istioctl` 1.31, `promtool` 3.x, Python 3 with PyYAML; a cluster for the schema checks; Istio and Argo Rollouts to run it for real |

!!! note "About the lab code"
    The checks **were run**: `istioctl` 1.31.1 validated and analysed the mesh manifests offline; promtool 3.15.0 evaluated the analysis queries; and on k3s v1.37.1, with Istio's and Argo Rollouts' CRDs installed (no controllers), the API server dry-ran every manifest. Istio and Argo Rollouts themselves weren't run: their images come from registries this lab couldn't reach. What their data plane does was measured in [chapter 57](../deployment/ch57-service-mesh.md) with Envoy directly, and canary analysis in [chapter 58](../deployment/ch58-cicd-gitops.md) with a controller of its own.

## 1. Who may call menu-service

```yaml title="istio/mesh-security.yaml"
--8<-- "code/code-lab/lab-16-istio-canary/istio/mesh-security.yaml"
```

- **`STRICT` mTLS**: every connection to a pod in the namespace must present a mesh certificate. A pod outside the mesh, or a port-forward around it, gets nothing.
- **Callers by identity**: the principals are SPIFFE identities from those certificates, one per service account. The list is generated from the platform model's call graph (`code/foodfleet/model/generated/policies/`), so a new caller needs a reviewed change to the model, not a firewall ticket ([ADR-038](../security/ch38-service-to-service-security.md)).
- **With an `ALLOW` policy present, everything else is denied**, including a caller that has a valid certificate but isn't listed.

Chapter 57 measured exactly these refusals with Envoy and a lab CA: plaintext, no certificate, the wrong identity and the right identity from another CA were all refused; only the listed identity got a 200.

## 2. Traffic: two Services, one name

```yaml title="istio/traffic.yaml"
--8<-- "code/code-lab/lab-16-istio-canary/istio/traffic.yaml"
```

Callers keep using `menu-service.menu-service.svc`. The mesh now retries, so callers keep their timeouts but drop their own retry (ADR-057): [lab 06](lab-06-resilience.md)'s `@Retry` would multiply attempts with these. Argo Rollouts points `menu-service-stable` and `menu-service-canary` at one version each and changes the two weights. The outlier detection is there because chapter 57's default configuration never ejected a pod that answered every request with 503.

## 3. The Rollout

```yaml title="rollouts/rollout.yaml"
--8<-- "code/code-lab/lab-16-istio-canary/rollouts/rollout.yaml"
```

- **`workloadRef`** keeps the shared chart's Deployment as the pod template, so the platform's rules still apply ([lab 15](lab-15-kubernetes-helm-keda.md)). The chart's HPA now targets the Rollout (`autoscaling.target: Rollout`, a value added to the chart for this lab).
- **The steps follow [ADR-058](../deployment/ch58-cicd-gitops.md#8-enterprise-case-study)**: 10, 25, 50, then 100%, five minutes of analysis at each. FoodFleet shifts the weights on a Gateway API `HTTPRoute` (through Argo Rollouts' Gateway API plugin; in ambient mode the waypoint implements the route). This lab uses Istio's `VirtualService`, which Argo Rollouts drives without a plugin; the steps and the analysis are the same either way.
- **`setCanaryScale: matchTrafficWeight`** gives the canary pods in proportion to its traffic. In chapter 58's first run a healthy release was aborted at 50% with no errors: its single pod took as much traffic as three stable pods and its p99 crossed the limit.

## 4. The analysis, tested before it runs

```yaml title="rollouts/analysis.yaml"
--8<-- "code/code-lab/lab-16-istio-canary/rollouts/analysis.yaml"
```

Each metric has three outcomes, as chapter 58's `CanaryAnalysis` does: **success**, **failure** (abort and shift all traffic back), and **inconclusive** when the canary has had fewer than 100 requests in the last minute (`-1`), which pauses the rollout for a person after four tries instead of promoting on no evidence.

A wrong query here fails silently: Argo Rollouts evaluates whatever Prometheus returns. `verify/test_analysis.py` reads the two queries from `analysis.yaml` and runs them with promtool against synthetic Istio metrics for four canaries:

```console
== 2. canary analysis queries
SUCCESS
canary                                       error-rate       verdict  p99 ratio       verdict
healthy, no 5xx at all                            0.000       success      1.000       success
5% errors                                         0.050       failure      1.000       success
too little traffic (60 requests a minute)        -1.000  inconclusive     -1.000  inconclusive
slow: p99 in the 100-200 ms bucket                0.000       success      2.000       failure
```

**The test caught a real bug.** The first version of the error-rate query divided `sum(rate(…5xx…))` by the total. A canary with **no** 5xx responses has no 5xx series at all, so the numerator was an empty vector, the division was empty, and the query fell through to `-1`. Every healthy canary would have been *inconclusive*, and every release would have stopped for a person. `(… or on() vector(0))` makes "no errors" a zero.

The p99 ratio is 2.0, not 199/99: Prometheus interpolates inside a histogram bucket, and both versions' requests sat in buckets starting at 50 and 100 ms. Latency is compared with the stable version rather than a fixed number (ADR-058: no more than 20% slower), so a busy node slows both and aborts neither.

## 5. Every field against the real schemas

```console
== 1. istioctl 1.31.1
"../istio/mesh-security.yaml" is valid
"../istio/traffic.yaml" is valid
✔ No validation issues found when analyzing:
== 3. dry run against the API server's schemas
  peerauthentication.security.istio.io/default created (server dry run)
  authorizationpolicy.security.istio.io/menu-service created (server dry run)
  virtualservice.networking.istio.io/menu-service created (server dry run)
  destinationrule.networking.istio.io/menu-service created (server dry run)
  rollout.argoproj.io/menu-service created (server dry run)
  analysistemplate.argoproj.io/menu-service-canary created (server dry run)
```

The schemas have teeth. `setWeight: ten` is refused (*"must be of type integer"*), and so is `mode: STRICTER` (*"supported values: UNSET, DISABLE, PERMISSIVE, STRICT"*). A dry run in CI catches these before Argo CD tries to sync them.

## What a canary buys

Chapter 58, with 100 requests a second and a release failing 5% of requests:

| Release | Requests that reached the bad version | Failed |
|---|---|---|
| Canary at 10%, analysis every 30 s | 192–223 (aborted after 19–22 s) | 7–10 |
| Rolling update, undone after 2 minutes | everyone | 944–953 |

## Run it

```bash
ISTIOCTL=istioctl PROMTOOL=promtool code/code-lab/lab-16-istio-canary/verify/run.sh      # offline checks
# with a cluster: also KUBECTL, HELM, ISTIO_CRDS (istio-1.31.1/manifests/charts/base/files/crd-all.gen.yaml)
# and ROLLOUTS_INSTALL (argo-rollouts v1.9.2 install.yaml)
```

To run it for real the way FoodFleet does ([ADR-057](../deployment/ch57-service-mesh.md#8-enterprise-case-study)): `istioctl install --set profile=ambient`, label the namespace `istio.io/dataplane-mode=ambient`, and add a waypoint for menu-service (`istioctl waypoint apply -n menu-service --enroll-namespace`), because the VirtualService's weights, retries and outlier detection are L7 features that ambient mode applies in the waypoint, not in the node's ztunnel. (With sidecars instead, label the namespace `istio-injection=enabled`.) Then install Argo Rollouts, apply the manifests, change the Deployment's image and watch `kubectl argo rollouts get rollout menu-service -n menu-service --watch`.

## Check yourself

- [ ] `run.sh` passes all three stages.
- [ ] You can explain why the healthy canary was inconclusive with the first query, and what `or on() vector(0)` changes.
- [ ] You can name what each of the four canaries would do to the rollout.

## Break it

!!! question "Exercises"
    1. Add a fifth scenario to `test_analysis.py`: 0.8% errors at 2 requests a second. What does the analysis decide, and is that what you want?
    2. Set the PeerAuthentication to `PERMISSIVE`. What can a pod outside the mesh now do, and which policy still stops it?
    3. Remove `setCanaryScale`. At 50%, how many requests a second does the one canary pod take compared with each stable pod?
    4. Compare the canary's p99 with a fixed 250 ms instead of the stable version. Construct the case where a healthy release is aborted.

**Next:** [Lab 17](lab-17-native-crac.md) shortens start time with an AOT cache, a native image or CRaC.
