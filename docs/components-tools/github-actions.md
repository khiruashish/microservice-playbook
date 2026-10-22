---
title: "GitHub Actions"
description: "FoodFleet's CI on GitHub Actions (ADR-058, ADR-041): build once, test, scan, sign keylessly and attest by digest, then open a pull request to the environment repository; CI holds no cluster credentials. Every third-party action pinned to a commit SHA, permissions empty at the top and granted per job, id-token: write only where signing needs it. Why: a moved action tag leaked a registry token; with a leaked key only provenance stopped a laptop rebuild; and accepting any GitHub Actions identity verified the wrong project's file."
progress: done
---

# GitHub Actions

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | GitHub's CI/CD: YAML workflows of jobs and steps triggered by repository events, running on hosted or self-hosted runners, with reusable actions, secrets, environments and an OIDC token per job for cloud and signing identities |
| **FoodFleet uses** | A build-once pipeline per service and a release workflow per tag; CI ends at a pull request to the environment repository |
| **Decided in** | [ADR-058](../deployment/ch58-cicd-gitops.md#8-enterprise-case-study), [ADR-041](../security/ch41-supply-chain-compliance.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 58](../deployment/ch58-cicd-gitops.md), [chapter 41](../security/ch41-supply-chain-compliance.md), [lab 19](../code-lab/lab-19-contracts-archunit.md) |
| **Related** | [Argo CD](argo-cd.md), [Cosign & Syft](cosign-syft.md), [Security scanners](security-scanners.md), [Jenkins & GitLab CI](jenkins-gitlab-ci.md) |

## The pipeline: CI never deploys

```yaml title="ci/pipeline.yaml (chapter 58)"
--8<-- "code/chapters/ch58-cicd-gitops/src/main/resources/ci/pipeline.yaml"
```

The image is built once (Jib), scanned, signed and attested **by digest**, and that digest is what every environment runs. Promotion to staging and prod is an approved pull request that copies the digest ([Helm](helm.md)); the GitOps controller applies it ([Argo CD](argo-cd.md)).

## The release workflow, hardened

```yaml title="ci/release.yml (chapter 41)"
--8<-- "code/chapters/ch41-supply-chain-compliance/src/main/resources/ci/release.yml"
```

| Rule | Why ([chapter 41](../security/ch41-supply-chain-compliance.md)) |
|---|---|
| **Actions pinned by SHA** (version in a comment, updated by Dependabot or Renovate) | A popular action's tag was moved to a commit that printed the runner's secrets: FoodFleet's registry token leaked |
| **`permissions: {}`**, granted per job | A workflow token with write to everything is the attacker's token |
| **Keyless signing** (`id-token: write`) | Fulcio issues a ~10-minute certificate naming this workflow; no key to leak. With a leaked key, a laptop rebuild **verified**; only provenance refused it |
| **Provenance** from `attest-build-provenance` | Names builder, source and ref, checked at admission |
| **`persist-credentials: false`** on checkout | Later steps can't push with the job's token |

Verifiers must pin the identity: `--certificate-identity-regexp '.*'` accepted **any** GitHub Actions workflow, including one in another project.

## Other workflows

- **Pull requests**: unit, slice and contract tests; API diff with oasdiff (continue-on-error until the baseline is clean, [lab 19](../code-lab/lab-19-contracts-archunit.md)); architecture tests.
- **Nightly**: component and end-to-end suites ([chapter 64](../testing/ch64-component-e2e-testing.md)); rescans of deployed digests ([Security scanners](security-scanners.md)).

## Pitfalls

- **Third-party actions by tag** (`@v4`).
- **`pull_request_target` checking out the PR's code** with secrets available.
- **Long-lived cloud keys in secrets** instead of OIDC federation.
- **Cluster credentials in CI**: chapter 58's early Jenkins job held a cluster-admin token.
- **Building per environment**: what staging tested isn't what prod runs.
- **Self-hosted runners shared across repositories** without isolation.
