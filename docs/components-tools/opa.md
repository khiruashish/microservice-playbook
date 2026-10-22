---
title: "Open Policy Agent"
description: "Policy as code with OPA and Rego: FoodFleet's compliance rules (images by digest from its registry, a declared data class, payment data only in the PCI namespace, personal and payment data in Indian regions, retention within three years, non-root containers) checked in CI and again at admission. Measured with OPA 1.21: 7 of 7 policy tests passed, and the policy found 9 violations in 2 of 4 sample deployments. Plus Rego in cosign's provenance check, and OPA against Kyverno and in-service authorization."
progress: done
---

# Open Policy Agent

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | A general-purpose policy engine: policies written in **Rego**, evaluated over JSON input (a Kubernetes object, a Terraform plan, an API request), returning decisions |
| **FoodFleet uses** | OPA 1.21 for compliance rules over declared data classes, in CI (`opa test`, conftest) and at admission; Rego for cosign's provenance policy; Kyverno for image signature admission |
| **Decided in** | [ADR-041](../security/ch41-supply-chain-compliance.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 41](../security/ch41-supply-chain-compliance.md), [chapter 35](../security/ch35-security-fundamentals.md) (authorization as a product) |
| **Related** | [Cosign & Syft](cosign-syft.md), [Kubernetes](kubernetes.md), [Terraform](terraform.md) |

## Compliance as code

Every deployment declares its **data class** (`public`, `internal`, `personal`, `payment`), its **region** and its **retention**; the policy turns PCI DSS, RBI's payment data storage rule and India's DPDP Rules into checks over those declarations.

```rego title="policy/deploy.rego (chapter 41)"
--8<-- "code/chapters/ch41-supply-chain-compliance/src/main/resources/policy/deploy.rego"
```

`deny` is a set of messages; empty means compliant. Each rule is a few lines, and each has tests:

```rego title="policy/deploy_test.rego (chapter 41)"
--8<-- "code/chapters/ch41-supply-chain-compliance/src/main/resources/policy/deploy_test.rego"
```

## Measured

`opa test` passed **7 of 7** tests. FoodFleet's four sample deployments:

| Deployment | Violations |
|---|---|
| order-service | 0 |
| payment-service | 0 |
| analytics-export | **4**: image by tag (`:latest`); not `runAsNonRoot`; **personal data in `eu-west-1`**; retention 3,650 days > 1,095 |
| coupon-legacy | **5**: two images from `docker.io`, one by tag; not `runAsNonRoot`; **payment data outside the `cde` namespace** |

The analytics export is the incident chapter 41 starts from: personal data copied to Europe by a job nobody had classified.

## The same language for provenance

cosign evaluates a Rego policy over an image's SLSA provenance, so a rebuild signed with a leaked key but built on a laptop is refused:

```rego title="cosign/provenance.rego (chapter 41)"
--8<-- "code/chapters/ch41-supply-chain-compliance/src/main/resources/cosign/provenance.rego"
```

## Where policies run

| Where | Tool | FoodFleet |
|---|---|---|
| Pull request | `opa test`, `conftest test` over rendered manifests | Compliance rules, before merge |
| Admission | OPA Gatekeeper, or Kyverno | Kyverno verifies signatures and provenance (failure policy Fail, monitored, with break-glass); the OPA rules run there too |
| Infrastructure plans | `conftest` over `terraform show -json` | Not applied in the chapters; the same region rules would fit |
| In a service, per request | OPA as a sidecar or library, or Cedar | Not used: authorization stays in each service's policy component ([chapter 35](../security/ch35-security-fundamentals.md)), tested exhaustively |

| | OPA / Gatekeeper | Kyverno |
|---|---|---|
| Language | Rego (general purpose; a learning curve) | YAML patterns, CEL |
| Beyond Kubernetes | Yes: any JSON (Terraform, APIs, CI) | Kubernetes only |
| Mutation, image verification | Gatekeeper mutation; signatures via external data | Built in (`verifyImages`, `mutateDigest`) |

FoodFleet uses both: Rego where the same rule must run in CI, at admission and over Terraform; Kyverno for image verification, which it does natively.

## Pitfalls

- **Policies without tests**: a typo in a rule passes everything.
- **Admission only**: developers learn of a violation at deploy time; run the same rules in the pull request.
- **Fail-open admission for security rules**, or fail-closed without monitoring the webhook.
- **Declared data classes that nobody checks against reality**: pair declarations with the data inventory.
- **Authorization logic copied into a central policy engine** without the stored object it needs: object-level decisions stay in the service.
