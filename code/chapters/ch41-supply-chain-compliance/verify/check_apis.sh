#!/usr/bin/env bash
# Checks each API, property, default and flag this chapter relies on: Spring Boot 4.1.1 sources, Kyverno 1.19.1's
# API types, the action.yml of actions/attest-build-provenance v4.2.2 and sigstore/cosign-installer v4.1.2, and the
# help output of the tool binaries used (cosign 3.1.3, syft 1.54.1, opa 1.21.2).
# Usage: ./check_apis.sh <boot> <kyverno> <attest-build-provenance> <cosign-installer> <bin dir>
set -uo pipefail
BOOT=$1 KY=$2 ATT=$3 CI=$4 BIN=$5
fail=0
check() {   # description, root, path, extended regex
  if grep -Eq -- "$4" "$2/$3" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "$3"; fail=$((fail + 1)); fi
}
helps() {   # description, extended regex, command...
  local d=$1 re=$2; shift 2
  local out; out=$("$@" 2>&1)
  if grep -Eq -- "$re" <<< "$out"; then printf 'OK       %s\n' "$d"; else printf 'MISSING  %s  (%s)\n' "$d" "$*"; fail=$((fail + 1)); fi
}
P=starter/spring-boot-starter-parent/build.gradle
S=module/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/sbom
K=api/kyverno/v1/image_verification_types.go

echo "-- Spring Boot 4.1.1"
check "starter-parent configures cyclonedx-maven-plugin (pluginManagement)"  "$BOOT" $P "delegate.artifactId\('cyclonedx-maven-plugin'\)"
check "...goal makeAggregateBom in generate-resources"             "$BOOT" $P "delegate.goal\('makeAggregateBom'\)"
check "...writes META-INF/sbom/application.cdx(.json)"             "$BOOT" $P "delegate.outputName\('application.cdx'\)"
check "CycloneDX Maven Plugin version managed: 2.9.3"              "$BOOT" platform/spring-boot-dependencies/build.gradle 'library\("CycloneDX Maven Plugin", "2.9.3"\)'
check "actuator endpoint id sbom"                                   "$BOOT" "$S/SbomEndpoint.java" '@Endpoint\(id = "sbom"\)'
check "...autodetects classpath:META-INF/sbom/application.cdx.json" "$BOOT" "$S/SbomEndpoint.java" 'classpath:META-INF/sbom/application.cdx.json'
check "management.endpoint.sbom.* properties"                       "$BOOT" "$S/SbomProperties.java" '@ConfigurationProperties\("management.endpoint.sbom"\)'

echo "-- Kyverno 1.19.1 (kyverno.io/v1 verifyImages)"
check "imageReferences"                                             "$KY" $K 'json:"imageReferences,omitempty"'
check "failureAction (Audit/Enforce) per verifyImages rule"         "$KY" $K 'json:"failureAction,omitempty"'
check "mutateDigest, default true"                                  "$KY" $K 'MutateDigest bool `json:"mutateDigest"`'
check "verifyDigest, default true"                                  "$KY" $K 'VerifyDigest bool `json:"verifyDigest"`'
check "required, default true"                                      "$KY" $K 'Required bool `json:"required"`'
check "keyless attestor: issuer / subjectRegExp"                    "$KY" $K 'SubjectRegExp string `json:"subjectRegExp,omitempty"`'
check "attestations[].type (predicateType deprecated)"              "$KY" $K "Deprecated in favour of 'Type'"
check "attestations[].conditions"                                   "$KY" $K 'Conditions \[\]AnyAllConditions `json:"conditions,omitempty"`'

echo "-- GitHub Actions"
check "attest-build-provenance: subject-name, subject-digest"       "$ATT" action.yml '^  subject-digest:'
check "attest-build-provenance: push-to-registry"                   "$ATT" action.yml '^  push-to-registry:'
check "cosign-installer: cosign-release input"                      "$CI" action.yml '^  cosign-release:'

echo "-- tools"
helps "cosign verify-blob --trusted-root (offline with a bundle)"    '--trusted-root' "$BIN/cosign" verify-blob --help
helps "cosign verify-blob --certificate-identity-regexp"             '--certificate-identity-regexp' "$BIN/cosign" verify-blob --help
helps "cosign --tlog-upload is deprecated (signing configs instead)" 'tlog-upload' "$BIN/cosign" sign --help
helps "cosign signing-config create"                                 'Create a Sigstore protobuf signing config' "$BIN/cosign" signing-config create --help
helps "cosign verify-attestation --policy (Rego or CUE)"             'REGO_POLICY' "$BIN/cosign" verify-attestation --help
helps "syft -o cyclonedx-json and spdx-json"                         'spdx-json' "$BIN/syft" --help
helps "opa test"                                                     'Execute Rego test cases' "$BIN/opa" test --help

echo
[ $fail -eq 0 ] && echo "all checks passed" || { echo "$fail checks failed"; exit 1; }
