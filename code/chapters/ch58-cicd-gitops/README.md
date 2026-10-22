# Chapter 58 — CI/CD, GitOps & Progressive Delivery

Code and measurements for [chapter 58](../../../docs/deployment/ch58-cicd-gitops.md).

| Path | What |
|---|---|
| `src/main/java/.../gitops/` | `Inventory` (objects in rendered manifests; what to prune) and `Reconciler` (fetch, render, server-side apply with forced ownership, drift detection with a server-side diff, pruning, an inventory kept in the cluster) |
| `src/main/java/.../delivery/` | `CanaryAnalysis` (promote, wait or abort: absolute and relative error rates, p99, minimum sample, early abort on a burst) |
| `src/main/resources/` | `gitops/menu-prod/` (kustomization, Deployment, Service, ConfigMap), `delivery/` (Traefik's Gateway API provider as a HelmChartConfig; a weighted HTTPRoute), `ci/pipeline.yaml` (build once, scan, sign, open a pull request; an example workflow, not run here) |
| `src/test/java/` | 5 tests |
| `verify/` | `build-libs.sh` (the menu-service image on chapter 54's runtime; kubectl, Gateway API and Argo CD source clones), `run.sh`, `check_apis.sh` (8 checks), `tools/` (`DeliveryLab`, `Measure`) |

```bash
K3S_DISABLE= ../../ch52-health-checks-actuator/verify/k3s-lab.sh   # a single-node cluster with Traefik
JAVA_HOME=/path/to/jdk-25 verify/run.sh                            # about 15 minutes
```

## Findings (k3s v1.37.1+k3s1, one 2-vCPU node, Traefik 3.7.13, kustomize 5.8.3, JDK 25.0.4, two runs)

**gitops**: the reconciler polled a local Git repository every 10 s.

| Change | Result |
|---|---|
| 5 commits | applied 1.0–9.8 s after the push; rolled out after 4.2–13.5 s |
| `kubectl scale --replicas=5` | back to 2 after 1.2–6.9 s (drift) |
| `kubectl set env VERSION=hotfix` | back to Git's value after 9.0–9.3 s (drift) |
| `kubectl set env DEBUG=true` (not in Git) | still there 30 s later; no drift detected |
| `kubectl delete deployment` | recreated and ready after 11.3–12.0 s |
| `git rm flags.yaml` | ConfigMap pruned after 7.1–7.9 s |

**canary**: stable 1.4.0 on 3 pods, canary 1.4.1 on 1 pod, weights 10/25/50/100, 30 s of analysis per step, 100 requests/s through the Gateway.

- Healthy: promoted in 121 s with 0 failures (run 2); aborted at 50% after 14 s with 0 errors (run 1), so on p99 over 250 ms.
- Failing 5%: aborted at 10% after 19–22 s; 192–223 requests reached it; 7–10 failed.
- Failing 5% as a rolling update, undone after 2 minutes: 944–953 failed.

**bluegreen**: Service selector switched between two 3-pod slots, 100 requests/s per path.

- New connections through the Service: all on the new slot after 0.2–0.8 s. Through the Gateway: 0.2–0.3 s. No failures.
- A keep-alive pool: still on the old slot 15 s later, every request.

**apis**: 8 of 8 found in kubectl 1.37.1, Gateway API 1.6.1 and Argo CD 3.5.5.

## Notes

- The reconciler runs inside `Measure`, not as a pod: the timings are the loop's, without Argo CD's or Flux's caching.
- A trial run before these two gave the same results within the ranges, except that the rolling update case failed 981 requests and the healthy canary was promoted.
- The first version of `Measure` reported each change's controller action one row late: a sync's effects show before the reconcile returns. It now waits for the sync's record.
- k3s's Traefik needs `servicelb` for an external address; the lab disabled it and sent traffic to Traefik's cluster IP.
