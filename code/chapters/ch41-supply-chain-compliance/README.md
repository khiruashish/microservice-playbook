# Chapter 41 — Supply-Chain Security & Compliance

| Package / file | What it is |
|---|---|
| `sbom/` | `Component` (package URLs: `pkg:maven/group/artifact@version`), `CycloneDx` (the components of a CycloneDX JSON SBOM, nested ones included) |
| `vuln/` | `MavenVersion` (Maven's version order: `2.0-rc1` < `2.0` = `2.0.Final` < `2.0-sp1`), `Advisory` + `OsvDatabase` (OSV advisories, e.g. a checkout of the GitHub Advisory Database), `Scanner` (SBOM × advisories, optionally as known on a date), `Gate` (block fixable critical/high findings; dated waivers) |
| `privacy/` | `CryptoShredder` (a data key per customer, wrapped by a KEK; erasing the key erases every copy), `InMemoryKeyStore` |
| `json/` | The strict JSON parser from chapter 36 |
| `resources/ci/release.yml` | The release workflow: actions pinned by SHA, per-job permissions, keyless signing of the digest, SBOM and SLSA provenance attestations |
| `resources/maven/`, `order-service.yml` | CycloneDX plugin and Maven Enforcer rules; the `sbom` actuator endpoint on the management port |
| `resources/kyverno/` | `ClusterPolicy` verifying signer identity, provenance and digests at admission |
| `resources/policy/`, `resources/k8s/` | OPA rules for registries, digests, data class, Indian regions (RBI), the PCI namespace, DPDP retention, with tests; four sample deployments |
| `resources/cosign/` | The provenance policy cosign evaluates, and two provenance predicates (release workflow, laptop) |
| `verify/` | `run.sh` (seven modes, below), `check_apis.sh`, `tools/Measure.java` |

## Run

```bash
cd code
mvn -B -pl chapters/ch41-supply-chain-compliance -am verify      # unit tests (JDK only); writes this module's own SBOM

# the experiments: a JDK, python3, openssl, git and access to github.com (release binaries, Keycloak archives,
# the GitHub Advisory Database, Sigstore's trusted root); about 1.5 GB of downloads, cached in verify/build
chapters/ch41-supply-chain-compliance/verify/run.sh
chapters/ch41-supply-chain-compliance/verify/run.sh shred        # the one that needs only a JDK
```

## What was verified, and how

- The JDK code has 12 unit tests, all passing here.
- `verify/run.sh` downloaded the release binaries of cosign 3.1.3, Syft 1.54.1, crane 0.22.1, the distribution
  registry 3.1.2 and OPA 1.21.2 and checked each against its published SHA-256; then verified cosign's and Syft's
  Sigstore bundles **offline** against Sigstore's trusted root taken from `sigstore/root-signing` (cosign normally gets
  it through TUF, which was not reachable here).
- SBOMs were made by Syft from four real Keycloak release archives (SHA-1 checked) and matched by this chapter's
  `Scanner` against the GitHub Advisory Database (7,181 reviewed Maven advisories, 13 October 2026). No other scanner's
  database was reachable, so the matcher was not cross-checked against Grype or Trivy; its version ordering and range
  semantics are covered by the unit tests (including the real Log4Shell advisory).
- Signing and attestation ran against a real local OCI registry with cosign and key pairs (no Rekor or Fulcio
  offline); the keyless flow and Kyverno were not run: `check_apis.sh` checks the Kyverno fields and defaults, the
  GitHub Actions inputs, Spring Boot's SBOM support and the tool flags used (25 checks).
- The OPA policies ran with `opa test` and `opa eval` against the sample manifests.

## Verified output

JDK 21.0.10 on one 2-vCPU container. Two runs with the same results except for timings and image digests (the
chapter quotes ranges). This is the second.

```text
== downloads
cosign v3.1.3 and syft 1.54.1 release files, verified offline against Sigstore's trusted root (sigstore/root-signing)
  cosign binary, identity pinned to cosign's release signer              verified
  the same binary with one byte changed                                  FAILED: signature does not match the file
  syft checksums, identity pinned to syft's release workflow             verified
  syft checksums, but expecting grype's release workflow                 FAILED: signed by a different identity
  syft checksums, any identity on GitHub Actions (regexp '.*')           verified
  syft tarball SHA-256 against the verified checksums file               verified
  syft's signing certificate: notBefore=Oct  6 15:34:03 2026 GMT | notAfter=Oct  6 15:44:03 2026 GMT | X509v3 Subject Alternative Name: critical | URI:https://github.com/anchore/syft/.github/workflows/release.yaml@refs/heads/main
  offline verification: 61 ms each (cosign process included)

== sbom
  Keycloak 22.0.0   500 jar files ->  460 Maven components (410 with coordinates from pom.properties, 50 guessed), 17.1 CPEs each; CycloneDX 1459 KB, SPDX 2062 KB; 6.6 s
  Keycloak 24.0.0   378 jar files ->  421 Maven components (387 with coordinates from pom.properties, 34 guessed), 17.0 CPEs each; CycloneDX 1319 KB, SPDX 1842 KB; 6.8 s
  Keycloak 26.0.0   424 jar files ->  446 Maven components (382 with coordinates from pom.properties, 64 guessed), 17.7 CPEs each; CycloneDX 1442 KB, SPDX 2012 KB; 5.8 s
  Keycloak 26.8.1   486 jar files ->  519 Maven components (443 with coordinates from pom.properties, 76 guessed), 17.4 CPEs each; CycloneDX 1661 KB, SPDX 2314 KB; 6.8 s

== scan
GitHub Advisory Database at 1f86fbe844, 2026-10-13
advisory database: 7181 Maven advisories loaded in 2.6 s

findings as of 2026-10-13 (advisories that affect a component's exact version)
Keycloak  released    components  critical  high   moderate  low    packages    fix available in dependencies scan time
22.0.0    2023-07-11  460         3         68     116       25     42          184 of 212    99 of 212       122 ms
24.0.0    2024-03-04  421         3         62     109       22     36          170 of 196    91 of 196       17 ms
26.0.0    2024-10-04  446         4         61     96        19     39          153 of 180    92 of 180       8 ms
26.8.1    2026-10-13  519         0         1      2         0      3           0 of 3        0 of 3          6 ms

the same artifacts, findings known at each point in time (critical + high in brackets)
Keycloak  at release     +3 months      +6 months      +1 year        +2 years       today         
22.0.0    1 (0)          4 (2)          11 (3)         36 (11)        65 (23)        212 (71)      
24.0.0    0 (0)          14 (4)         18 (6)         36 (13)        78 (17)        196 (65)      
26.0.0    0 (0)          7 (4)          18 (7)         30 (10)        180 (65)       180 (65)      
26.8.1    3 (1)          -              -              -              -              3 (1)         

CI gate (fail on critical/high with a fix available), today:
  Keycloak 22.0.0   FAIL   blocking 69   reported without blocking 143
  Keycloak 24.0.0   FAIL   blocking 63   reported without blocking 133
  Keycloak 26.0.0   FAIL   blocking 62   reported without blocking 118
  Keycloak 26.8.1   pass   blocking 0    reported without blocking 3

Keycloak 26.8.1, critical and high findings today:
  HIGH     CVE-2026-2092        org.keycloak:keycloak-services@26.8.1                fixed in (none)

Keycloak 22.0.0, critical findings today:
  CVE-2026-75595       io.netty:netty-handler@4.1.94.Final                  published 2026-09-08, fixed in 4.1.137.Final
  CVE-2023-7272        org.eclipse.parsson:parsson@1.1.2                    published 2024-07-17, fixed in 1.1.3
  CVE-2024-1597        org.postgresql:postgresql@42.6.0                     published 2024-02-21, fixed in 42.6.1

== sign
order-service 1.4.0 pushed to a local registry, signed by digest with FoodFleet's key, with SLSA provenance
  verify the digest with FoodFleet's key                                   verified
  verify the tag 1.4.0 (resolves to the signed digest)                     verified
  verify with the attacker's key                                           REFUSED: not signed with this key
  provenance from the release workflow, policy cosign/provenance.rego      verified
the tag 1.4.0 now points to a laptop rebuild (sha256:bfa91e404e19d7a5c7bdc87713f5e5fb1dd98e254f5ff780fd9edcb27e59ad79 instead of sha256:7821e899d1e0a64b0373f8cbc068e1d1e7f639e0d2ff47fd55d08f5c76595d0b)
  verify the tag 1.4.0 (unsigned rebuild)                                  REFUSED: no signature for this digest
  verify the original digest                                               verified
... and signs the rebuild with FoodFleet's key, attaching provenance from the laptop
  verify the rebuild's signature with FoodFleet's key                      verified
  the rebuild's provenance against the policy                              REFUSED: policy denied (allow is false)
  SBOM attestation read back from the registry: 1003 components, predicate https://cyclonedx.org/bom
  sign: 645 ms, verify: 80 ms (each a cosign process against a local registry)

== policy
  opa test: PASS: 7/7
  analytics-export       4 violation(s)
      - export: image "registry.foodfleet.in/analytics/export:latest" is not pinned by digest
      - export: runAsNonRoot is not set
      - personal data outside India: region "eu-west-1"
      - retention of 3650 days exceeds 1095
  coupon-legacy          5 violation(s)
      - app: image "docker.io/foodfleet/coupon:2.3@sha256:d68fca6c7b19330d65e574d4bd3c5538d65c3435f6b30034637ba72ed0b79875" is not from registry.foodfleet.in/
      - migrate: image "docker.io/library/flyway:9" is not from registry.foodfleet.in/
      - migrate: image "docker.io/library/flyway:9" is not pinned by digest
      - migrate: runAsNonRoot is not set
      - payment data outside the cardholder data environment namespace (cde)
  order-service          0 violation(s)
  payment-service        0 violation(s)

== shred
20000 customers x 3 personal fields, encrypted with a data key per customer (AES-256-GCM),
copied as written into three places: the orders table, a Kafka topic, last night's backup
  encrypt: 21.9 µs per field; decrypt: 8.0 µs per field (data-key cache of 1,000)
  size: 82 bytes of plaintext became 236 bytes of stored text per customer
  erase 1000 customers: 1.7 µs each (delete one key row)
  readable afterwards, erased customers: 0 of 9000 fields; everyone else: 171000 of 171000
  key table: 19000 rows of 60 bytes

== apis
-- Spring Boot 4.1.1
OK       starter-parent configures cyclonedx-maven-plugin (pluginManagement)
OK       ...goal makeAggregateBom in generate-resources
OK       ...writes META-INF/sbom/application.cdx(.json)
OK       CycloneDX Maven Plugin version managed: 2.9.3
OK       actuator endpoint id sbom
OK       ...autodetects classpath:META-INF/sbom/application.cdx.json
OK       management.endpoint.sbom.* properties
-- Kyverno 1.19.1 (kyverno.io/v1 verifyImages)
OK       imageReferences
OK       failureAction (Audit/Enforce) per verifyImages rule
OK       mutateDigest, default true
OK       verifyDigest, default true
OK       required, default true
OK       keyless attestor: issuer / subjectRegExp
OK       attestations[].type (predicateType deprecated)
OK       attestations[].conditions
-- GitHub Actions
OK       attest-build-provenance: subject-name, subject-digest
OK       attest-build-provenance: push-to-registry
OK       cosign-installer: cosign-release input
-- tools
OK       cosign verify-blob --trusted-root (offline with a bundle)
OK       cosign verify-blob --certificate-identity-regexp
OK       cosign --tlog-upload is deprecated (signing configs instead)
OK       cosign signing-config create
OK       cosign verify-attestation --policy (Rego or CUE)
OK       syft -o cyclonedx-json and spdx-json
OK       opa test

all checks passed

```
