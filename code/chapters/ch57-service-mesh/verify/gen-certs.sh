#!/usr/bin/env bash
# The mesh's certificates, as a mesh CA (istiod, Linkerd's identity service) would issue them: an EC P-256 root, and a
# short-lived leaf per workload with its SPIFFE identity as a URI SAN. Plus the probes' negative cases: a valid
# certificate for an identity menu-service doesn't admit, and the right identity signed by another CA.
# Usage: ./gen-certs.sh <dir> [namespace]
set -euo pipefail
D=$1; NS=${2:-mesh}; TD=foodfleet.local
mkdir -p "$D" && cd "$D"
key() { openssl ecparam -name prime256v1 -genkey -noout -out "$1.key" 2>/dev/null; }
ca() { key "$1"; openssl req -x509 -new -key "$1.key" -subj "/O=FoodFleet/CN=$2" -days 3650 -out "$1.crt" 2>/dev/null; }
leaf() {   # name, service account, CA
  key "$1"
  openssl req -new -key "$1.key" -subj "/O=FoodFleet" -out "$1.csr" 2>/dev/null
  printf 'subjectAltName=URI:spiffe://%s/ns/%s/sa/%s\nextendedKeyUsage=serverAuth,clientAuth\nkeyUsage=digitalSignature\n' "$TD" "$NS" "$2" > "$1.ext"
  openssl x509 -req -in "$1.csr" -CA "$3.crt" -CAkey "$3.key" -CAcreateserial -days 1 -extfile "$1.ext" -out "$1.crt" 2>/dev/null
  openssl pkcs12 -export -in "$1.crt" -inkey "$1.key" -name "$1" -passout pass:changeit -out "$1.p12"
  rm -f "$1.csr" "$1.ext"
}
ca ca "mesh root CA"
ca rogue-ca "some other CA"
leaf menu menu-service ca
leaf gateway gateway ca
leaf reporting reporting ca
leaf rogue-gateway gateway rogue-ca
chmod 644 ./*.key
