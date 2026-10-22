---
title: "Cosign & Syft"
description: "Signing, attesting and inventorying FoodFleet's images (ADR-041): Syft for CycloneDX SBOMs, cosign for keyless signatures by digest in GitHub Actions, SLSA provenance, and Kyverno verifying signer identity, provenance and digests at admission. Measured with cosign 3.1.3 and Syft 1.54.1: a one-byte change and a wrong signer failed verification, but an identity regexp of .* verified a file from the wrong project; a moved tag was refused; a rebuild signed with a leaked key verified, and only provenance refused it."
progress: done
---

# Cosign & Syft

<div class="badges" markdown><span class="badge badge-l3">L3 · Senior/Lead</span><span class="badge badge-l4">L4 · Architect</span></div>

| | |
|---|---|
| **What it is** | **Syft** (Anchore): generates SBOMs (CycloneDX, SPDX) from images, directories and archives. **cosign** (Sigstore): signs and verifies OCI artifacts and attestations, with keys or keylessly (Fulcio certificates from an OIDC identity, logged in Rekor) |
| **FoodFleet uses** | Syft SBOMs as image attestations; cosign keyless signing by digest in CI; GitHub's SLSA provenance; Kyverno `verifyImages` in production namespaces |
| **Decided in** | [ADR-041](../security/ch41-supply-chain-compliance.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 41](../security/ch41-supply-chain-compliance.md) |
| **Related** | [Security scanners](security-scanners.md), [GitHub Actions](github-actions.md), [OPA](opa.md), [Docker](docker.md) |

## Verify identities, not just signatures

[Chapter 41](../security/ch41-supply-chain-compliance.md), offline against Sigstore's trusted root:

| Check | Result |
|---|---|
| cosign binary, signer pinned to cosign's release identity | verified |
| Same binary, one byte changed | **failed** |
| syft's checksums, expecting grype's release workflow | **failed**: different identity |
| syft's checksums, **any identity on GitHub Actions** (`--certificate-identity-regexp '.*'`) | **verified** |

syft's signing certificate was valid for ten minutes and still verified a week later: the transparency log entry proves it was used while valid. A checksum next to a download proves the download, not the publisher.

## Sign digests, check provenance

| Check (order-service 1.4.0, local registry) | Result |
|---|---|
| The digest, with FoodFleet's key | verified |
| The digest, with a different key | refused |
| *Tag moved to an unsigned laptop rebuild:* the tag | **refused** |
| *Rebuild signed with FoodFleet's (leaked) key:* its signature | **verified** |
| …its provenance against the policy | **refused** |

A key proves possession of a key. Provenance names the builder, source repository and ref, and the policy checks them:

```rego title="cosign/provenance.rego (chapter 41)"
--8<-- "code/chapters/ch41-supply-chain-compliance/src/main/resources/cosign/provenance.rego"
```

Keyless signing in CI removes the long-lived key entirely. Signing took 645–690 ms and verifying 66–80 ms.

## Enforced at admission

```yaml title="kyverno/verify-images.yaml (chapter 41)"
--8<-- "code/chapters/ch41-supply-chain-compliance/src/main/resources/kyverno/verify-images.yaml"
```

Kyverno checks issuer and subject (not "signed by someone on GitHub"), requires the provenance attestation, and `mutateDigest` pins the pod to the verified digest so the node can't pull something else. Failure policy Fail, monitored, with break-glass.

## SBOMs with Syft

Syft found 378–500 jar files in Keycloak releases in 5.8–7.2 s, producing 1.3–1.6 MB of CycloneDX; 8–15% of coordinates were guessed from file names. FoodFleet also writes a build-time CycloneDX SBOM into the jar (served at `/actuator/sbom`) and attests the image's SBOM, which reads back from the registry intact (1,003 entries for Keycloak 26.8.1).

## Pitfalls

- **`--certificate-identity-regexp '.*'`** or any unanchored pattern.
- **Signing or verifying tags**.
- **Signatures without provenance** where keys exist.
- **Admission that verifies, then lets the node pull by tag**.
- **Unverified tool downloads** in the pipeline that does the verifying.
