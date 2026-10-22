---
title: "Terraform & OpenTofu"
description: "Infrastructure as code for what FoodFleet runs beneath Kubernetes (ADR-059: EKS with Karpenter, EKS Pod Identity with one role per service account, MSK, Aurora PostgreSQL), with Terraform or OpenTofu: modules per cloud, state locked and encrypted, plans reviewed and policy-checked. No ADR chooses the tool or its pipeline, so this page sets out the practices and how the handbook's measured findings apply: credentials federated rather than stored (a pod's projected token lived 600 s, a default one a year), and compliance rules from chapter 41 that could run over plans."
progress: done
---

# Terraform & OpenTofu

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | Declarative infrastructure as code: HCL configurations of providers and resources, organised in modules; `plan` shows the diff against recorded **state**, `apply` makes it. **OpenTofu** is the Linux Foundation fork of Terraform's last open-source version (Terraform moved to the Business Source License in 2023) |
| **FoodFleet uses** | Not chosen by an ADR. What it would manage is: EKS and Karpenter, IAM roles and Pod Identity associations, MSK, Aurora PostgreSQL, networking ([ADR-059](../deployment/ch59-cloud-native-platforms.md#8-enterprise-case-study)) |
| **Learn it in** | [Chapter 59](../deployment/ch59-cloud-native-platforms.md) |
| **Related** | [OPA](opa.md), [Kubernetes](kubernetes.md), [Argo CD](argo-cd.md), [Vault](vault.md) |

## Where the line is

| Layer | Tool | Why |
|---|---|---|
| Cloud accounts, networks, clusters, managed services, IAM | Terraform / OpenTofu | Long-lived, slow-changing, outside Kubernetes |
| Platform components in the cluster (operators, ingress) | kustomize overlays via Argo CD ([ADR-055](../deployment/ch55-helm-kustomize.md#8-enterprise-case-study)) | Reconciled continuously; drift reverted |
| Services | The shared Helm chart via Argo CD | Same |

Keeping Kubernetes objects out of Terraform avoids two tools owning the same field, the conflict [Helm](helm.md) showed with `replicas`.

## Practices

- **Remote state, locked and encrypted** (S3 with locking, or a managed backend); state holds secrets such as generated passwords, so access is restricted like a secret store.
- **Modules per cloud**, versioned; [chapter 59](../deployment/ch59-cloud-native-platforms.md) recommends portability where it's cheap (Kubernetes manifests, OpenTelemetry, Kafka and PostgreSQL protocols, Terraform modules per cloud) and accepting cloud-specific identity and managed services.
- **Plans in pull requests**, applied from CI with a federated role, never from laptops; no long-lived access keys (ADR-059: Service Control Policies block creating them).
- **Small state files** per environment and component, so a plan is readable and a mistake's blast radius small.
- **Drift detection**: scheduled `plan` runs that alert on differences.

## Identity, measured

ADR-059 gives every service an IAM role through EKS Pod Identity. Chapter 59 measured the federation that underlies it:

| Token | Lifetime |
|---|---:|
| Projected, `expirationSeconds: 600` | 600 s |
| Default service account token | **365 days** |

A cloud's token service checks only the signature and expiry, so a token copied out of a deleted pod was still accepted 120 s later; short lifetimes are the control. The roles, trust policies and associations Terraform creates are where those limits are set (one role per service account, sessions of one hour).

## Policy over plans

The compliance rules in [OPA](opa.md) (personal and payment data in Indian regions, the PCI namespace, retention) are written over declared data classes. The same Rego can run with `conftest` over `terraform show -json` to stop, for example, a bucket for personal data in `eu-west-1` before it exists. Chapter 41 applied the rules to deployments; plans are the natural next place.

## Pitfalls

- **Local state**, or state in Git.
- **`apply` from laptops** with personal admin credentials.
- **One giant state** for everything.
- **Secrets as plain variables** in state and logs; read them from the secret store at apply time and mark outputs sensitive.
- **Managing Kubernetes workloads in Terraform** alongside a GitOps controller.
