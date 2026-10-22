---
title: "Part 8 — Deployment & Platform"
description: "How FoodFleet's services get from a commit to a running, scaled, secure deployment: images, Kubernetes, Helm and kustomize, autoscaling, a service mesh, GitOps and canaries, cloud platforms and fast startup, each measured on Docker, a real k3s cluster and the JVM itself."
progress: done
---

# Part 8 — Deployment & Platform

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

Parts 1 to 7 built services that are correct, resilient and observable. Part 8 is how they run:

- **Packaging:** images that are small, start fast and stop cleanly.
- **Running:** Kubernetes workloads, configuration, scaling and disruption.
- **Changing:** releases through Git, canaries and rollbacks.
- **Hosting:** cloud identity, managed services, functions, and the JVM's startup.

The recurring lesson is that the platform's defaults are tuned for someone else's workload: CPU limits that throttle JVM startup, ConfigMap changes that never reach an environment variable, keep-alive clients that never see new pods, a scale-down window that's right and a scale-up that counts warm-up as load, an outlier detector that never ejects, a hand edit GitOps can't see, a token that outlives its pod.

Every chapter measures its claims on real systems:

- Docker 29 and a local registry; a k3s 1.37 cluster with Traefik 3.7.
- Helm 4.1 and kustomize 5.8; Envoy 1.39; a GitOps reconciler and Gateway API canaries.
- The AWS SDK's retry algorithms; JDK 25's AOT cache and GraalVM 25 native images.

The measured output is in the text, and the code to reproduce it is in the repository.

## FoodFleet's platform

| Layer | Question | FoodFleet's choice | Owner | Chapters |
|---|---|---|---|---|
| Image | What ships? | Multi-stage builds to jlink'd runtimes on distroless, layered, non-root, read-only; exec-form entrypoints; SBOM, scan and signature | Platform + teams | [53](ch53-containerization.md) |
| Workload | How does a JVM run in a pod? | CPU requests without limits, memory limit = request, watched config mounts, PDBs with zone spread | Platform | [54](ch54-kubernetes-for-spring.md) |
| Packaging | How is it deployed per environment? | One shared Helm chart; kustomize for platform pieces; promotion by digest; config tied to the pod template | Platform | [55](ch55-helm-kustomize.md) |
| Scaling | How many pods? | CPU HPAs at 60% with readiness after warm-up; scheduled minimums for meal peaks; KEDA for consumers | Teams + platform | [56](ch56-autoscaling.md) |
| Network | How do services talk safely? | Istio ambient: mTLS and identity authorization everywhere; waypoints for per-request balancing and outlier detection | Platform | [57](ch57-service-mesh.md) |
| Delivery | How does a change reach production? | Build and sign once; PRs to an environment repo; Argo CD sync; canaries on Gateway API weights | Platform + teams | [58](ch58-cicd-gitops.md) |
| Cloud | What does the cloud add? | EKS, pod identity per service account, standard/adaptive SDK retries, MSK and Aurora, Lambda for edges | Platform | [59](ch59-cloud-native-platforms.md) |
| Startup | How fast is a new pod useful? | AOT cache in every JVM image, trained through HTTP; native images for functions and jobs | Teams + platform | [60](ch60-native-images-startup.md) |

## Decision guide

| If your problem is… | Reach for | Chapter | What the chapter measured |
|---|---|---|---|
| A SIGTERM that's ignored | Exec-form entrypoints | [53](ch53-containerization.md) | Shell-form and wrapper entrypoints made `docker stop` wait 10 s and kill the service |
| Slow, throttled JVM startup in pods | CPU requests without limits | [54](ch54-kubernetes-for-spring.md) | At a 250m limit: 3.9–4.0 s to start against 0.7–0.8 s, p99 93–97 ms against 10–16 ms |
| Config changes that don't arrive | Mounted, watched config, or a restart on change | [54](ch54-kubernetes-for-spring.md), [55](ch55-helm-kustomize.md) | Without a checksum or hashed name, no pod restarted and the environment variable stayed old |
| New pods that get no traffic | Connection lifetime caps, or a mesh | [54](ch54-kubernetes-for-spring.md), [57](ch57-service-mesh.md) | A keep-alive client sent every request to the first pod for 50 s; through Envoy 333/333/334 |
| Outages during node drains | A PodDisruptionBudget | [54](ch54-kubernetes-for-spring.md) | 295–331 failed requests without one, 0–1 with `minAvailable: 2` |
| Deploys that fight other controllers | Server-side apply; no `replicas` in the manifest | [55](ch55-helm-kustomize.md) | Helm 4 failed with a field conflict where Helm 3 silently reset 5 replicas to 2 |
| Two versions behind one Service | Promotion by digest | [55](ch55-helm-kustomize.md) | A moved tag ran two builds on a scale-out; digests kept all four pods on one |
| An HPA that overshoots | Readiness after warm-up | [56](ch56-autoscaling.md) | Ready-at-once pods drove the HPA to 8 pods for 4 pods' work; held until warm, 5 |
| Flapping replica counts | A scale-down stabilization window | [56](ch56-autoscaling.md) | No window: 10–11 changes and 325–432 failures in 8 minutes; default: 2 changes |
| One bad pod in three | Retries plus outlier detection | [57](ch57-service-mesh.md) | 1,000 of 3,000 failed with neither; 0 with both and 35 retries; Envoy's 10% default never ejected |
| Bad releases reaching everyone | Canaries with per-version analysis | [58](ch58-cicd-gitops.md) | Aborted at 10% after 7–10 failures, against 944–953 for a rolling update |
| Drift from hand edits | GitOps with self-heal and prune | [58](ch58-cicd-gitops.md) | Commits applied in 1.0–9.8 s; scaled and deleted objects restored; a hand-added variable stayed |
| Cloud keys in Secrets | Workload identity | [59](ch59-cloud-native-platforms.md) | Wrong audience, account and signature refused; a deleted pod's token still verified offline |
| Throttled cloud APIs | Adaptive retries with a quota | [59](ch59-cloud-native-platforms.md) | Failed calls at twice the limit: 141–161 adaptive, 766–914 legacy and standard |
| Functions or containers | Traffic shape and the cold start | [59](ch59-cloud-native-platforms.md) | $0.01–0.10 a day against $2.37 for quiet services; containers win only when they follow load at scale |
| Slow JVM starts | An AOT cache trained through HTTP; native for functions | [60](ch60-native-images-startup.md) | 1 CPU: 1.1–1.2 s JVM, 0.6–0.7 s AOT cache, 0.25–0.27 s native; native served 27–42% less after a minute |

## The chapters

<div class="grid cards" markdown>

-   **[53. Containerization](ch53-containerization.md)**

    ---

    Five ways to package a service, JVM container ergonomics, AOT caches, PID 1, SBOMs. *Docker and a local registry.*

-   **[54. Kubernetes for Spring Developers](ch54-kubernetes-for-spring.md)**

    ---

    CPU limits, ConfigMaps, scale-outs with keep-alive clients, drains and PDBs. *A k3s 1.37 cluster.*

-   **[55. Helm & Kustomize](ch55-helm-kustomize.md)**

    ---

    Server-side apply, config rollouts, failed releases and rollback, tags vs digests. *Helm 4.1 and kustomize 5.8.*

-   **[56. Autoscaling](ch56-autoscaling.md)**

    ---

    HPA timing, warm-up over-scaling, the cost of scale events, flapping. *A real HPA under load.*

-   **[57. Service Mesh](ch57-service-mesh.md)**

    ---

    Per-request balancing, mTLS identities, retries and outlier detection, sidecar cost. *Envoy 1.39 sidecars.*

-   **[58. CI/CD, GitOps & Progressive Delivery](ch58-cicd-gitops.md)**

    ---

    A reconciler, drift and pruning, canaries on Gateway API weights, blue/green. *Traefik 3.7 on k3s.*

-   **[59. Cloud-Native on AWS, Azure & GCP](ch59-cloud-native-platforms.md)**

    ---

    Workload identity, throttled cloud APIs and SDK retries, functions vs containers. *k3s as the OIDC issuer.*

-   **[60. Native Images, CRaC & Startup Optimization](ch60-native-images-startup.md)**

    ---

    JVM, warm-up, AppCDS, AOT cache and native image at 1 and 0.25 CPU; CRaC from its sources. *JDK 25 and GraalVM 25.*

</div>

## Reading order by experience

| You are | Read | Then |
|---|---|---|
| **Building services already** (🔵 L2) | 53 → 54 → 55 → 58 | [Part 9: Testing](../testing/index.md) |
| **Leading a team** (🟣 L3) | 53 → 54 → 56 → 58 → 60 | [Part 9: Testing](../testing/index.md) |
| **Designing a platform** (🔴 L4) | 54 → 57 → 58 → 59 | [Part 9: Testing](../testing/index.md) |
| **Preparing for interviews** | 54, 56, 59 (each ends with 12 questions) | [Interview prep](../interview-prep/index.md) |

## How the FoodFleet story builds up

<ul class="mh-timeline">
<li><span class="when">ADR-053 · Chapter 53</span><span class="what">Multi-stage builds to jlink'd runtimes on distroless; exec-form entrypoints; non-root, read-only; AOT caches for scale-out services; SBOM, scan and signature per image.</span></li>
<li><span class="when">ADR-054 · Chapter 54</span><span class="what">CPU requests without limits; memory limit equal to request; configuration as watched mounts or restart-on-change; PDBs with zone spread; connection lifetime caps.</span></li>
<li><span class="when">ADR-055 · Chapter 55</span><span class="what">One shared Helm chart; kustomize for platform components; promotion by digest; server-side apply; rollouts awaited with automatic rollback.</span></li>
<li><span class="when">ADR-056 · Chapter 56</span><span class="what">CPU HPAs at 60% with readiness gated on warm-up; the default five-minute scale-down window; scheduled minimums before meal peaks; KEDA for consumers.</span></li>
<li><span class="when">ADR-057 · Chapter 57</span><span class="what">Istio ambient with strict mTLS and identity authorization; waypoints for L7 balancing, retries and outlier detection; client retries dropped.</span></li>
<li><span class="when">ADR-058 · Chapter 58</span><span class="what">Build and sign once; PRs to an environment repository; Argo CD with sync, prune and self-heal; Argo Rollouts canaries on Gateway API weights.</span></li>
<li><span class="when">ADR-059 · Chapter 59</span><span class="what">EKS with Karpenter; Pod Identity per service account; standard or adaptive SDK retries; MSK and Aurora; Lambda with SnapStart at the edges.</span></li>
<li><span class="when">ADR-060 · Chapter 60</span><span class="what">An AOT cache in every JVM image, trained through HTTP and checked in CI; capped warm-up before readiness; native images for functions and jobs.</span></li>
</ul>

!!! tip "Running the code"
    ```bash
    cd code
    mvn -B -pl chapters/ch53-containerization,chapters/ch54-kubernetes-for-spring,chapters/ch55-helm-kustomize,chapters/ch56-autoscaling,chapters/ch57-service-mesh,chapters/ch58-cicd-gitops,chapters/ch59-cloud-native-platforms,chapters/ch60-native-images-startup -am verify
    ```
    JDK 25 and Maven 3.9+. Each chapter's `verify/` folder holds its experiments and says what they need:

    - **53 and 60:** Docker (60 also GraalVM CE 25).
    - **54 to 58:** a Kubernetes cluster (chapter 52's `k3s-lab.sh` starts one); 55 also Helm and kustomize, 57 Envoy.
    - **59:** a cluster for the identity part; Docker for the cold starts.
