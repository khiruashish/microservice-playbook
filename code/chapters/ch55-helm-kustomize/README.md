# Chapter 55 — Helm, Kustomize & Environment Promotion

Code and measurements for [chapter 55](../../../docs/deployment/ch55-helm-kustomize.md).

| Path | What |
|---|---|
| `src/main/java/.../promotion/` | `ImageRef` (repository, tag, digest; registry ports handled) and `PromotionPolicy` (outside dev every image pinned by digest; prod runs exactly staging's digests; the pipeline's one edit to an overlay) |
| `src/main/resources/helm/foodfleet-service/` | One chart for every service: values per environment, a `checksum/config` annotation, `replicas` omitted under an HPA, a `fail` when no image is given |
| `src/main/resources/kustomize/` | `base/` (hashed `configMapGenerator`) and `overlays/{dev,staging,prod}` (namespace, replicas, digest, HPA) |
| `src/test/java/` | 5 tests |
| `verify/` | `build-libs.sh` (kustomize 5.8.3, Helm 4.1.4, two menu-service images, Helm and kustomize source clones), `registry-lab.sh` (a local registry k3s pulls from), `run.sh`, `check_apis.sh` (12 checks), `tools/` (`VersionedService`, `Measure`) |

```bash
../../ch52-health-checks-actuator/verify/k3s-lab.sh      # a single-node cluster, if needed
verify/registry-lab.sh                                   # a registry it pulls from; restart k3s once afterwards
JAVA_HOME=/path/to/jdk-25 verify/run.sh                  # about 25 minutes
```

## Findings (k3s v1.37.1+k3s1, one 2-vCPU node, Helm 4.1.4, kustomize 5.8.3, distribution 3.1.2, JDK 25.0.4, two runs)

**render**: every environment rendered in 30–62 ms with either tool and passed the API server's dry-run. The dry-run also accepted a prod release with a digest placeholder and one with a mutable tag; only `PromotionPolicy` rejected them. The chart's `fail` stopped a release with no image at render time.

**ownership**: `replicas: 2` deployed, then scaled to 5 and hand-edited (`set env FEATURE_X=on`), then re-applied. Identical in both runs.

| Method | Re-apply | Replicas after |
|---|---|---|
| `kubectl apply` (client-side) | ok | 2 |
| `kubectl apply --server-side` | conflict (owned by kubectl's `/scale`) | 5 |
| ...with `--force-conflicts` | ok | 2 |
| ...manifest without `replicas` | ok | 5 |
| `helm upgrade` (Helm 4, server-side) | conflict; release marked failed | 5 |
| ...with `--force-conflicts` | ok | 2 |
| `helm upgrade --server-side=false` | ok | 2 |

`FEATURE_X` survived every method.

**config**: discount 10 → 20, redeployed, polled for 150 s.

- Checksum annotation or hashed generator name: 2 of 2 pods replaced; env var and file both 20 within 5–9 s.
- Neither: 0 of 2 pods replaced; env var stayed 10; mounted file updated after 2–3 s (run 2) or 76–88 s (run 1).

**rollback**: 3 replicas of 1.4.0 under 20 requests/s; 1.4.1 crashes on start.

- `helm upgrade --rollback-on-failure --timeout 60s`: exit 1 after 61 s, back on 1.4.0 (history: 1 superseded, 2 failed, 3 deployed), 0 failed requests.
- `kubectl apply`, `rollout status --timeout 60s`, `rollout undo`: same outcome after 61 s; 0 failed requests in run 1, 13 of 1,383 in run 2 (see notes).
- `kubectl apply` alone: exit 0 at once; one pod crash-looping for good; 0 failed (the default strategy rounds `maxUnavailable` to 0 at 3 replicas). With `maxUnavailable: 1, maxSurge: 0`: two ready pods left, 0–1 failed.

**tags**: `stable` moved from 1.4.0 to 1.4.1 under 2 running pods, then scaled to 4. Identical in both runs.

- `IfNotPresent` on a node with the image: 4 × 1.4.0.
- `IfNotPresent` on a fresh node, or `Always`: 2 × 1.4.0 and 2 × 1.4.1.
- By digest: 4 × 1.4.0.

**apis**: 12 of 12 found in Helm 4.1.4 and kustomize 5.8.3.

## Notes

- get.helm.sh isn't reachable from the lab's network, so the lab used the Helm 4.1.4 binary shipped in k3s's klipper-helm image (same version and commit, `05fa379`).
- Run 2's `rollout undo` case had 13 failed requests; runs 1 and 3 had none. A third run of `rollback`, with each failure's time and cause logged, didn't reproduce them, so their cause is unknown. `Measure` now prints the time and cause of any failure.
- Pods pull through a k3s registry mirror (`/etc/rancher/k3s/registries.yaml`). "Fresh node" removes the image from the node with `crictl rmi` before the scale-out.
