#!/usr/bin/env bash
# Chapter 41 measurements.
#   ./run.sh                    everything (needs github.com: release downloads and git; python3, openssl)
#   ./run.sh shred              the one that needs only a JDK
# Modes:
#   downloads  verify release downloads: checksums vs Sigstore bundles, offline, with a pinned signer identity
#   sbom       Syft SBOMs (CycloneDX and SPDX) of four Keycloak releases
#   scan       match the SBOMs against the GitHub Advisory Database (Maven), today and over time
#   sign       a local OCI registry: sign by digest, move a tag, attest SLSA provenance, check it with a policy
#   policy     OPA tests and FoodFleet's manifests against the deployment rules
#   shred      crypto-shredding: erasure that reaches backups and topics
set -euo pipefail
cd "$(dirname "$0")"
MODES=${*:-downloads sbom scan sign policy shred apis}
B=build; mkdir -p $B/bin $B/keycloak $B/src
SRC=../src/main/java
rm -rf $B/main $B/tools && mkdir -p $B/main $B/tools
javac -d $B/main $(find $SRC -name '*.java')
cp -r ../src/main/resources/. $B/main/
javac -d $B/tools -cp $B/main tools/Measure.java
CP=$B/main:$B/tools
GH=https://github.com
COSIGN=v3.1.3 SYFT=1.54.1 CRANE=v0.22.1 REGISTRY=3.1.2 OPA=v1.21.2
KEYCLOAK="22.0.0=2023-07-11 24.0.0=2024-03-04 26.0.0=2024-10-04 26.8.1=2026-10-13"

fetch() {   # url file
  [ -s "$2" ] || curl -fsSL -o "$2" "$1"
}
clone() {   # url tag dir paths...
  local url=$1 tag=$2 dir=$B/src/$3; shift 3
  if [ ! -d "$dir/.git" ]; then
    git -c advice.detachedHead=false clone -q --depth 1 ${tag:+--branch "$tag"} --filter=blob:none --sparse "$url" "$dir"
    git -C "$dir" sparse-checkout set --no-cone "$@"
  fi
}
tools() {
  cd $B/bin
  fetch $GH/sigstore/cosign/releases/download/$COSIGN/cosign-linux-amd64 cosign
  fetch $GH/sigstore/cosign/releases/download/$COSIGN/cosign_checksums.txt cosign_checksums.txt
  fetch $GH/sigstore/cosign/releases/download/$COSIGN/cosign-linux-amd64.sigstore.json cosign.sigstore.json
  fetch $GH/anchore/syft/releases/download/v$SYFT/syft_${SYFT}_linux_amd64.tar.gz syft.tar.gz
  fetch $GH/anchore/syft/releases/download/v$SYFT/syft_${SYFT}_checksums.txt syft_checksums.txt
  fetch $GH/anchore/syft/releases/download/v$SYFT/syft_${SYFT}_checksums.txt.sigstore.json syft_checksums.txt.sigstore.json
  fetch $GH/google/go-containerregistry/releases/download/$CRANE/go-containerregistry_Linux_x86_64.tar.gz gcr.tar.gz
  fetch $GH/google/go-containerregistry/releases/download/$CRANE/checksums.txt gcr_checksums.txt
  fetch $GH/distribution/distribution/releases/download/v$REGISTRY/registry_${REGISTRY}_linux_amd64.tar.gz registry.tar.gz
  fetch $GH/distribution/distribution/releases/download/v$REGISTRY/registry_${REGISTRY}_linux_amd64.tar.gz.sha256 registry.sha256
  fetch $GH/open-policy-agent/opa/releases/download/$OPA/opa_linux_amd64_static opa
  fetch $GH/open-policy-agent/opa/releases/download/$OPA/opa_linux_amd64_static.sha256 opa.sha256
  # published checksums first (integrity of the download; the signatures are checked in "downloads")
  [ "$(sha256sum cosign | cut -c1-64)" = "$(grep ' cosign-linux-amd64$' cosign_checksums.txt | cut -c1-64)" ]
  [ "$(sha256sum syft.tar.gz | cut -c1-64)" = "$(grep "syft_${SYFT}_linux_amd64.tar.gz" syft_checksums.txt | cut -c1-64)" ]
  [ "$(sha256sum gcr.tar.gz | cut -c1-64)" = "$(grep 'Linux_x86_64.tar.gz' gcr_checksums.txt | cut -c1-64)" ]
  [ "$(sha256sum registry.tar.gz | cut -c1-64)" = "$(cut -c1-64 registry.sha256)" ]
  [ "$(sha256sum opa | cut -c1-64)" = "$(cut -c1-64 opa.sha256)" ]
  [ -x syft ] || tar -xzf syft.tar.gz syft
  [ -x crane ] || tar -xzf gcr.tar.gz crane
  [ -x registry ] || tar -xzf registry.tar.gz registry
  chmod +x cosign opa
  cd - > /dev/null
}
BIN=$PWD/$B/bin
if [ "$MODES" != shred ]; then
  tools
  # Sigstore's trusted root (Fulcio CAs, Rekor and TSA keys). cosign normally fetches it through TUF; offline, it is
  # taken from the root-signing repository it is published from.
  clone $GH/sigstore/root-signing.git "" root-signing /targets/trusted_root.json
fi
TRUSTED_ROOT=$B/src/root-signing/targets/trusted_root.json

why() {   # cosign's error output -> the reason, in words
  case "$1" in
    *"no signatures found"*) echo "no signature for this digest" ;;
    *"accepted signatures do not match threshold"*) echo "not signed with this key" ;;
    *"expression value, false, is not true"*) echo "policy denied (allow is false)" ;;
    *"no matching CertificateIdentity"*) echo "signed by a different identity" ;;
    *"invalid signature"*) echo "signature does not match the file" ;;
    *) echo "$1" | grep -m1 -i error | cut -c1-80 ;;
  esac
}

for m in $MODES; do
  echo "== $m"
  case $m in
    downloads)
      row() {   # description, command...
        local d=$1; shift
        if out=$("$@" 2>&1); then printf '  %-70s %s\n' "$d" "verified"; else printf '  %-70s %s\n' "$d" "FAILED: $(why "$out")"; fi
      }
      GHA=https://token.actions.githubusercontent.com
      echo "cosign $COSIGN and syft $SYFT release files, verified offline against Sigstore's trusted root (sigstore/root-signing)"
      row "cosign binary, identity pinned to cosign's release signer" \
        $BIN/cosign verify-blob $BIN/cosign --bundle $BIN/cosign.sigstore.json --trusted-root $TRUSTED_ROOT \
        --certificate-oidc-issuer https://accounts.google.com --certificate-identity keyless@projectsigstore.iam.gserviceaccount.com
      cp $BIN/cosign $B/cosign-tampered && printf '\x01' | dd of=$B/cosign-tampered bs=1 seek=4096 conv=notrunc 2>/dev/null
      row "the same binary with one byte changed" \
        $BIN/cosign verify-blob $B/cosign-tampered --bundle $BIN/cosign.sigstore.json --trusted-root $TRUSTED_ROOT \
        --certificate-oidc-issuer https://accounts.google.com --certificate-identity keyless@projectsigstore.iam.gserviceaccount.com
      row "syft checksums, identity pinned to syft's release workflow" \
        $BIN/cosign verify-blob $BIN/syft_checksums.txt --bundle $BIN/syft_checksums.txt.sigstore.json --trusted-root $TRUSTED_ROOT \
        --certificate-oidc-issuer $GHA --certificate-identity $GH/anchore/syft/.github/workflows/release.yaml@refs/heads/main
      row "syft checksums, but expecting grype's release workflow" \
        $BIN/cosign verify-blob $BIN/syft_checksums.txt --bundle $BIN/syft_checksums.txt.sigstore.json --trusted-root $TRUSTED_ROOT \
        --certificate-oidc-issuer $GHA --certificate-identity $GH/anchore/grype/.github/workflows/release.yaml@refs/heads/main
      row "syft checksums, any identity on GitHub Actions (regexp '.*')" \
        $BIN/cosign verify-blob $BIN/syft_checksums.txt --bundle $BIN/syft_checksums.txt.sigstore.json --trusted-root $TRUSTED_ROOT \
        --certificate-oidc-issuer $GHA --certificate-identity-regexp '.*'
      row "syft tarball SHA-256 against the verified checksums file" \
        sh -c "[ \"\$(sha256sum $BIN/syft.tar.gz | cut -c1-64)\" = \"\$(grep linux_amd64.tar.gz $BIN/syft_checksums.txt | cut -c1-64)\" ]"
      python3 - "$BIN/syft_checksums.txt.sigstore.json" <<'PY'
import base64, json, subprocess, sys
b = json.load(open(sys.argv[1]))["verificationMaterial"]
der = base64.b64decode(b["certificate"]["rawBytes"])
out = subprocess.run(["openssl", "x509", "-inform", "der", "-noout", "-startdate", "-enddate", "-ext", "subjectAltName"],
                     input=der, capture_output=True).stdout.decode()
lines = [l.strip() for l in out.splitlines() if l.strip()]
print("  syft's signing certificate:", " | ".join(lines))
PY
      t0=$(date +%s%N)
      for _ in 1 2 3 4 5; do $BIN/cosign verify-blob $BIN/syft_checksums.txt --bundle $BIN/syft_checksums.txt.sigstore.json \
        --trusted-root $TRUSTED_ROOT --certificate-oidc-issuer $GHA \
        --certificate-identity $GH/anchore/syft/.github/workflows/release.yaml@refs/heads/main > /dev/null 2>&1; done
      echo "  offline verification: $(( ($(date +%s%N) - t0) / 5000000 )) ms each (cosign process included)" ;;
    sbom)
      for kv in $KEYCLOAK; do
        v=${kv%%=*}; f=$B/keycloak/keycloak-$v.tar.gz
        fetch $GH/keycloak/keycloak/releases/download/$v/keycloak-$v.tar.gz $f
        fetch $GH/keycloak/keycloak/releases/download/$v/keycloak-$v.tar.gz.sha1 $f.sha1
        [ "$(sha1sum $f | cut -c1-40)" = "$(cut -c1-40 $f.sha1)" ]
        d=$B/keycloak/kc-$v; [ -d $d ] || { mkdir -p $d && tar -xzf $f -C $d; }
        s=$(date +%s%N)
        SYFT_CHECK_FOR_APP_UPDATE=false $BIN/syft scan dir:$d -q -o cyclonedx-json=$B/keycloak/sbom-$v.cdx.json -o spdx-json=$B/keycloak/sbom-$v.spdx.json
        ms=$(( ($(date +%s%N) - s) / 1000000 ))
        python3 - "$v" "$B/keycloak/sbom-$v.cdx.json" "$B/keycloak/sbom-$v.spdx.json" "$ms" "$(find $d -name '*.jar' | wc -l)" <<'PY'
import json, os, sys
v, cdx, spdx, ms, jars = sys.argv[1:]
c = json.load(open(cdx))["components"]
maven = [x for x in c if x.get("purl", "").startswith("pkg:maven/")]
pom = sum(1 for x in maven if any(p["name"] == "syft:metadata:-:groupID" for p in x.get("properties", [])))
cpes = sum(1 for x in maven for p in x.get("properties", []) if p["name"] == "syft:cpe23")
print(f"  Keycloak {v:7} {jars:>4} jar files -> {len(maven):>4} Maven components ({pom} with coordinates from pom.properties, "
      f"{len(maven) - pom} guessed), {cpes / max(1, len(maven)):.1f} CPEs each; CycloneDX {os.path.getsize(cdx) // 1024} KB, "
      f"SPDX {os.path.getsize(spdx) // 1024} KB; {int(ms) / 1000:.1f} s")
PY
      done ;;
    scan)
      clone $GH/github/advisory-database.git "" advisory-database /advisories/github-reviewed/
      echo "GitHub Advisory Database at $(git -C $B/src/advisory-database log -1 --format='%h, %cs')"
      ARGS=""
      for kv in $KEYCLOAK; do v=${kv%%=*}; ARGS="$ARGS $v=${kv#*=}=$B/keycloak/sbom-$v.cdx.json"; done
      java -Xmx3g -Dstdout.encoding=UTF-8 -cp $CP Measure scan $B/src/advisory-database/advisories/github-reviewed \
        "$(git -C $B/src/advisory-database log -1 --format=%cs)" $ARGS ;;
    sign)
      W=$B/sign; rm -rf $W; mkdir -p $W/reg
      cat > $W/reg.yml <<YML
version: 0.1
storage:
  filesystem:
    rootdirectory: $PWD/$W/reg
http:
  addr: 127.0.0.1:5041
log:
  level: error
YML
      if (exec 3<>/dev/tcp/127.0.0.1/5041) 2>/dev/null; then echo "port 5041 is in use"; exit 1; fi
      $BIN/registry serve $W/reg.yml > $W/reg.log 2>&1 &
      REG_PID=$!
      trap 'kill $REG_PID 2>/dev/null || true' EXIT
      sleep 1
      export COSIGN_PASSWORD="lab-$(openssl rand -hex 8)"
      CO="$BIN/cosign"; CR="$BIN/crane"; R=127.0.0.1:5041/foodfleet/order-service
      NET="--allow-http-registry --allow-insecure-registry"
      (cd $W && $CO generate-key-pair --output-key-prefix foodfleet > /dev/null 2>&1 && $CO generate-key-pair --output-key-prefix attacker > /dev/null 2>&1)
      $CO signing-config create --out $W/no-tlog.json > /dev/null   # a lab without Rekor: keys only
      mkdir -p $W/app && echo "order-service 1.4.0" > $W/app/VERSION && tar -C $W -cf $W/layer1.tar app
      echo "order-service 1.4.0, rebuilt from someone's laptop" > $W/app/VERSION && tar -C $W -cf $W/layer2.tar app
      $CR append -f $W/layer1.tar -t $R:1.4.0 --insecure > /dev/null 2>&1
      D1=$($CR digest $R:1.4.0 --insecure)
      s=$(date +%s%N); $CO sign --key $W/foodfleet.key --signing-config $W/no-tlog.json $NET -y $R@$D1 > /dev/null 2>&1
      sign_ms=$(( ($(date +%s%N) - s) / 1000000 ))
      $CO attest --key $W/foodfleet.key --signing-config $W/no-tlog.json --type slsaprovenance1 --predicate ../src/main/resources/cosign/provenance-release.json $NET -y $R@$D1 > /dev/null 2>&1
      check() {   # description, cosign args...
        local d=$1; shift
        if out=$($CO "$@" $NET 2>&1); then printf '  %-72s %s\n' "$d" "verified"; else printf '  %-72s %s\n' "$d" "REFUSED: $(why "$out")"; fi
      }
      echo "order-service 1.4.0 pushed to a local registry, signed by digest with FoodFleet's key, with SLSA provenance"
      s=$(date +%s%N); check "verify the digest with FoodFleet's key" verify --key $W/foodfleet.pub --insecure-ignore-tlog=true $R@$D1
      verify_ms=$(( ($(date +%s%N) - s) / 1000000 ))
      check "verify the tag 1.4.0 (resolves to the signed digest)" verify --key $W/foodfleet.pub --insecure-ignore-tlog=true $R:1.4.0
      check "verify with the attacker's key" verify --key $W/attacker.pub --insecure-ignore-tlog=true $R@$D1
      check "provenance from the release workflow, policy cosign/provenance.rego" verify-attestation --key $W/foodfleet.pub \
        --insecure-ignore-tlog=true --type slsaprovenance1 --policy ../src/main/resources/cosign/provenance.rego $R@$D1
      # someone rebuilds on a laptop, pushes the same tag, and signs it: the key is FoodFleet's (it leaked, or CI reused it)
      $CR append -f $W/layer2.tar -t $R:1.4.0 --insecure > /dev/null 2>&1
      D2=$($CR digest $R:1.4.0 --insecure)
      echo "the tag 1.4.0 now points to a laptop rebuild ($D2 instead of $D1)"
      check "verify the tag 1.4.0 (unsigned rebuild)" verify --key $W/foodfleet.pub --insecure-ignore-tlog=true $R:1.4.0
      check "verify the original digest" verify --key $W/foodfleet.pub --insecure-ignore-tlog=true $R@$D1
      $CO sign --key $W/foodfleet.key --signing-config $W/no-tlog.json $NET -y $R@$D2 > /dev/null 2>&1
      $CO attest --key $W/foodfleet.key --signing-config $W/no-tlog.json --type slsaprovenance1 --predicate ../src/main/resources/cosign/provenance-laptop.json $NET -y $R@$D2 > /dev/null 2>&1
      echo "... and signs the rebuild with FoodFleet's key, attaching provenance from the laptop"
      check "verify the rebuild's signature with FoodFleet's key" verify --key $W/foodfleet.pub --insecure-ignore-tlog=true $R@$D2
      check "the rebuild's provenance against the policy" verify-attestation --key $W/foodfleet.pub \
        --insecure-ignore-tlog=true --type slsaprovenance1 --policy ../src/main/resources/cosign/provenance.rego $R@$D2
      # an SBOM attestation travels with the image: fetch it back and scan what is deployed
      $CO attest --key $W/foodfleet.key --signing-config $W/no-tlog.json --type cyclonedx --predicate $B/keycloak/sbom-26.8.1.cdx.json $NET -y $R@$D1 > /dev/null 2>&1
      $CO verify-attestation --key $W/foodfleet.pub --insecure-ignore-tlog=true --type cyclonedx $NET $R@$D1 2>/dev/null \
        | head -1 | python3 -c "import sys,json,base64; s=json.loads(base64.b64decode(json.load(sys.stdin)['payload'])); print('  SBOM attestation read back from the registry:', len(s['predicate']['components']), 'components, predicate', s['predicateType'])"
      echo "  sign: $sign_ms ms, verify: $verify_ms ms (each a cosign process against a local registry)"
      kill $REG_PID; trap - EXIT ;;
    policy)
      P=../src/main/resources/policy
      $BIN/opa test $P | tail -1 | sed 's/^/  opa test: /'
      for f in ../src/main/resources/k8s/*.yaml; do
        n=$($BIN/opa eval -d $P/deploy.rego -i $f 'count(data.foodfleet.deploy.deny)' -f raw)
        printf '  %-22s %s violation(s)\n' "$(basename $f .yaml)" "$n"
        $BIN/opa eval -d $P/deploy.rego -i $f 'data.foodfleet.deploy.deny' -f raw | python3 -c "import json,sys; [print('      -', m) for m in sorted(json.load(sys.stdin))]"
      done ;;
    shred)
      java -Dstdout.encoding=UTF-8 -cp $CP Measure shred ;;
    apis)
      clone $GH/spring-projects/spring-boot.git v4.1.1 boot /starter/spring-boot-starter-parent/build.gradle \
        /platform/spring-boot-dependencies/build.gradle /module/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/sbom/ \
        /module/spring-boot-actuator-autoconfigure/src/main/java/org/springframework/boot/actuate/autoconfigure/sbom/
      clone $GH/kyverno/kyverno.git v1.19.1 kyverno /api/kyverno/v1/image_verification_types.go
      clone $GH/actions/attest-build-provenance.git v4.2.2 attest /action.yml
      clone $GH/sigstore/cosign-installer.git v4.1.2 cosign-installer /action.yml
      ./check_apis.sh $B/src/boot $B/src/kyverno $B/src/attest $B/src/cosign-installer $BIN ;;
    *) echo "unknown mode $m"; exit 1 ;;
  esac
  echo
done
