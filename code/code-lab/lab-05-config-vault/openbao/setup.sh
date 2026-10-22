#!/usr/bin/env bash
# A local OpenBao for the lab: dev mode (in memory, unsealed, root token printed). Never for anything real.
#   bao server -dev -dev-root-token-id=lab-root &
set -euo pipefail
export BAO_ADDR=http://127.0.0.1:8200 BAO_TOKEN=${BAO_TOKEN:-lab-root}
bao kv put secret/menu-service partner-api-key=pk_test_lab05
# a policy that can read only menu-service's secrets, and a token for the service with only that policy
bao policy write menu-service - <<'POLICY'
path "secret/data/menu-service" { capabilities = ["read"] }
path "secret/data/application" { capabilities = ["read"] }
POLICY
bao token create -policy=menu-service -ttl=1h -field=token
