---
title: "Jenkins & GitLab CI"
description: "Jenkins and GitLab CI against FoodFleet's pipeline rules: FoodFleet started on a Jenkins job per service that ran kubectl apply against production with a cluster-admin token, and three weeks of incidents (an unrecorded manual scale, a deleted ConfigMap that stayed, a bad release on every pod for eleven minutes) moved it to build-once CI plus GitOps (ADR-058). What carries over to any CI server: no cluster credentials, build once and promote digests, pinned dependencies, short-lived identities for signing."
progress: done
---

# Jenkins & GitLab CI

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | **Jenkins**: a self-hosted automation server with Groovy pipelines (`Jenkinsfile`) and a large plugin ecosystem. **GitLab CI/CD**: pipelines defined in `.gitlab-ci.yml`, built into GitLab, run by GitLab runners, with environments, OIDC ID tokens and built-in security scanning |
| **FoodFleet uses** | Formerly Jenkins; now GitHub Actions for CI and Argo CD for delivery |
| **Decided in** | [ADR-058](../deployment/ch58-cicd-gitops.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 58](../deployment/ch58-cicd-gitops.md), [chapter 41](../security/ch41-supply-chain-compliance.md) |
| **Related** | [GitHub Actions](github-actions.md), [Argo CD](argo-cd.md), [Cosign & Syft](cosign-syft.md) |

## What FoodFleet's Jenkins job taught

[Chapter 58](../deployment/ch58-cicd-gitops.md): a Jenkins job per service built, tested and ran `kubectl apply` against production with a **cluster-admin token stored in Jenkins**. Three weeks in a row:

1. Someone scaled order-service by hand during an incident; the cluster ran an unrecorded configuration for a month.
2. A ConfigMap deleted from the repository kept existing, and a feature flag nobody could find in Git stayed on.
3. A release failing 5% of checkouts reached every pod in a rolling update; it took eleven minutes to notice and roll back.

None of these is Jenkins' fault. Push-based deploys don't see drift or deletions, and a rolling update has no analysis. The fix was the delivery model: a GitOps controller that reverted a manual scale within one polling interval and pruned a deleted ConfigMap in 7–8 s, and canaries that stopped a bad release after about 200 requests instead of 12,000.

## Rules for any CI server

| Rule | Jenkins | GitLab CI |
|---|---|---|
| **No cluster credentials in CI**; end at a pull request to the environment repo | Remove kubeconfig credentials; a job opens the PR | Same; or GitLab's agent for Kubernetes in pull mode |
| **Build once, promote the digest** | Record the digest as a build artifact | `CI_JOB` artifacts / container registry digest |
| **Pinned dependencies** | Pin plugin versions; shared libraries by tag *and* reviewed | `include:` templates by SHA or `ref` you control |
| **Short-lived signing identity** | Keyless signing needs an OIDC provider; often a stored key instead (protect, rotate) | `id_tokens:` give jobs an OIDC token for Sigstore |
| **Least privilege** | Folder-scoped credentials, agents per trust level | Protected branches and environments, scoped variables |

## Choosing

| | Jenkins | GitLab CI | GitHub Actions |
|---|---|---|---|
| Hosting | Self-hosted | SaaS or self-managed | SaaS (+ self-hosted runners) |
| Pipeline language | Groovy DSL | YAML | YAML |
| Extensibility | Plugins (a supply chain of their own) | Templates, components | Actions (pin by SHA) |
| Fits | Existing estates, unusual build hardware | Teams on GitLab wanting one platform | Teams on GitHub |

## Pitfalls

- **Deploying from CI with admin credentials**.
- **Unpinned plugins and shared libraries** auto-updated on the controller.
- **Pipelines per environment** that rebuild.
- **Secrets printed in logs**: mask, and never `set -x` around them.
