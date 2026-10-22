---
title: "Security Scanners"
description: "Vulnerability scanning at FoodFleet (ADR-041): SBOMs from the build and the image, CI gated on critical or high findings that have a fix (with waivers that expire), and a daily rescan of every deployed digest, because scans expire. Measured against the GitHub Advisory Database: Keycloak 24.0.0 shipped with 0 known findings and had 196 (65 critical or high) two and a half years later; image SBOMs guessed 8–15% of coordinates; scanning took 7–25 ms per SBOM. Grype, Trivy and OSV-Scanner compared."
progress: done
---

# Security Scanners

<div class="badges" markdown><span class="badge badge-l2">L2 · Practitioner</span><span class="badge badge-l3">L3 · Senior/Lead</span></div>

| | |
|---|---|
| **What it is** | Tools that match software inventories (SBOMs, lockfiles, images) against vulnerability databases (GitHub Advisory Database, OSV, NVD, distro feeds): **Grype**, **Trivy**, **OSV-Scanner**, OWASP Dependency-Check, Dependabot/Renovate for updates |
| **FoodFleet uses** | CycloneDX SBOMs from Maven and from the image (Syft); a CI gate on fixable critical/high findings; daily rescans of deployed digests; Dependabot or Renovate for Maven and actions |
| **Decided in** | [ADR-041](../security/ch41-supply-chain-compliance.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 41](../security/ch41-supply-chain-compliance.md) |
| **Related** | [Cosign & Syft](cosign-syft.md), [GitHub Actions](github-actions.md), [Docker](docker.md) |

## Scans expire

[Chapter 41](../security/ch41-supply-chain-compliance.md) matched unchanged Keycloak releases against what was known at each date (all findings, critical + high in brackets):

| Keycloak | At release | +3 months | +1 year | +2 years | Today |
|---|---:|---:|---:|---:|---:|
| 22.0.0 | 1 (0) | 4 (2) | 36 (11) | 65 (23) | **212 (71)** |
| 24.0.0 | **0 (0)** | 14 (4) | 36 (13) | 78 (17) | **196 (65)** |
| 26.0.0 | 0 (0) | 7 (4) | 30 (10) | 180 (65) | 180 (65) |

A clean scan describes one day. FoodFleet keeps an inventory of deployed digests and rescans them daily; Log4Shell had taken nine days to scope without one.

## Gate on what can be fixed

```java title="Gate.java (chapter 41)"
--8<-- "code/chapters/ch41-supply-chain-compliance/src/main/java/dev/foodfleet/ch41/vuln/Gate.java:35:53"
```

With the rule "critical or high, with a fixed version", the three older releases would be blocked by **62–69 findings** each; the current one (3 findings, none fixable yet) passes with them reported. Waivers carry an expiry date. Most findings were in dependencies, not Keycloak's own code (99 of 212 for 22.0.0).

## SBOMs: what a scanner can see

| Source | Coordinates | Sees |
|---|---|---|
| Build (CycloneDX Maven plugin) | Exact | Maven dependencies only |
| Image (Syft) | **8–15% guessed** for Keycloak's jars; ~17 candidate CPEs per component | Everything with package metadata in the image |

FoodFleet keeps both. An image built by copying glibc without the Debian package database showed no glibc at all, so a scanner would never flag it ([Docker](docker.md)).

## Choosing a scanner

| | Grype | Trivy | OSV-Scanner |
|---|---|---|---|
| Input | Images, SBOMs, directories | Images, file systems, repos, IaC, Kubernetes | Lockfiles, SBOMs, directories |
| Database | Anchore's (GHSA, NVD, distros) | Trivy DB (distros, GHSA, NVD) | OSV.dev |
| Beyond CVEs | — | Misconfigurations, secrets, licences | Call analysis for some ecosystems |

All read CycloneDX or SPDX, so the SBOM is the stable interface; scanning took **7–25 ms per SBOM** once the database was loaded (2.6–2.7 s). Database updates are what change results.

## Pitfalls

- **Scanning only at build time**.
- **Gating on everything, including unfixable findings**: teams learn to waive blindly.
- **Image-only SBOMs** with guessed coordinates, or build-only SBOMs that miss the OS.
- **Ignoring dependency updates**: merge Dependabot/Renovate pull requests weekly.
- **Scanner binaries and databases downloaded unverified** ([Cosign & Syft](cosign-syft.md)).
