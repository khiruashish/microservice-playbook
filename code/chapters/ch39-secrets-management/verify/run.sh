#!/usr/bin/env bash
# Chapter 39 measurements.
#   ./run.sh                     everything
#   ./run.sh leaks delivery      only the experiments that need nothing but a JDK
# leaks and delivery need only a JDK (21+). static, dynamic and revocation also need:
#   - PostgreSQL 16 reachable as a superuser with psql (PGHOST, PGPORT, PGUSER; trust or .pgpass), and accepting
#     SCRAM-SHA-256 password logins over TCP at APP_PGHOST:APP_PGPORT (default 127.0.0.1:$PGPORT)
#   - the OpenBao binary (BAO_BIN, default: bao on the PATH); run.sh starts a dev server on 127.0.0.1:8299 and
#     stops it at the end. Creates and drops the database c39 and roles c39_*.
set -euo pipefail
cd "$(dirname "$0")"
SRC=../src/main/java
rm -rf build/main build/tools && mkdir -p build/main build/tools
javac -d build/main $(find $SRC -name '*.java' -not -path '*/web/*')
javac -d build/tools -cp build/main tools/Pg.java tools/Measure.java
CP=build/main:build/tools
MODES=${*:-leaks delivery static dynamic revocation}

needs_db=false
for m in $MODES; do case $m in static|dynamic|revocation) needs_db=true;; esac; done
if $needs_db; then
  export APP_PGHOST=${APP_PGHOST:-127.0.0.1} APP_PGPORT=${APP_PGPORT:-${PGPORT:-5432}} C39_DB=c39
  export C39_ADMIN_USER=c39_admin C39_ADMIN_PASSWORD="admin-$(openssl rand -hex 12)"
  BAO_ADMIN_PASSWORD="bao-$(openssl rand -hex 12)"
  export PGOPTIONS='-c client_min_messages=warning'
  psql -q -v ON_ERROR_STOP=1 -d postgres <<SQL
DROP DATABASE IF EXISTS c39 WITH (FORCE);
DO \$\$ DECLARE r record; BEGIN
  FOR r IN SELECT rolname FROM pg_roles WHERE rolname LIKE 'v-%' OR rolname LIKE 'c39_%' LOOP
    EXECUTE format('DROP ROLE %I', r.rolname);
  END LOOP; END \$\$;
CREATE ROLE c39_admin LOGIN CREATEROLE PASSWORD '$C39_ADMIN_PASSWORD';
CREATE ROLE c39_bao LOGIN CREATEROLE PASSWORD '$BAO_ADMIN_PASSWORD';
GRANT pg_signal_backend TO c39_bao;
CREATE ROLE c39_app_blue LOGIN;
CREATE ROLE c39_app_green LOGIN;
GRANT c39_app_blue, c39_app_green TO c39_admin WITH ADMIN OPTION;
CREATE DATABASE c39;
SQL
  psql -q -v ON_ERROR_STOP=1 -d c39 <<SQL
CREATE SCHEMA orders;
CREATE TABLE orders.orders (id bigint PRIMARY KEY, total numeric);
INSERT INTO orders.orders SELECT g, g * 10 FROM generate_series(1, 100) g;
GRANT USAGE ON SCHEMA orders TO c39_bao WITH GRANT OPTION;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA orders TO c39_bao WITH GRANT OPTION;
GRANT USAGE ON SCHEMA orders TO c39_app_blue, c39_app_green;
GRANT SELECT ON ALL TABLES IN SCHEMA orders TO c39_app_blue, c39_app_green;
SQL
  BAO=${BAO_BIN:-bao}
  export BAO_ADDR=http://127.0.0.1:8299 BAO_TOKEN="root-$(openssl rand -hex 12)"
  "$BAO" server -dev -dev-root-token-id="$BAO_TOKEN" -dev-listen-address=127.0.0.1:8299 > build/bao.log 2>&1 &
  BAO_PID=$!
  trap 'kill $BAO_PID 2>/dev/null || true' EXIT
  for _ in $(seq 50); do "$BAO" status > /dev/null 2>&1 && break; sleep 0.2; done
  "$BAO" secrets enable database > /dev/null
  "$BAO" write database/config/c39 plugin_name=postgresql-database-plugin \
    connection_url="postgresql://{{username}}:{{password}}@$APP_PGHOST:$APP_PGPORT/c39" \
    username=c39_bao password="$BAO_ADMIN_PASSWORD" \
    allowed_roles="order-service,order-service-short,order-service-strict,payment-service" > /dev/null
  CREATE="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; GRANT USAGE ON SCHEMA orders TO \"{{name}}\"; GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA orders TO \"{{name}}\";"
  "$BAO" write database/roles/order-service db_name=c39 default_ttl=1h max_ttl=24h creation_statements="$CREATE" > /dev/null
  "$BAO" write database/roles/order-service-short db_name=c39 default_ttl=6s max_ttl=18s creation_statements="$CREATE" > /dev/null
  "$BAO" write database/roles/order-service-strict db_name=c39 default_ttl=1h max_ttl=24h creation_statements="$CREATE" \
    revocation_statements="SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE usename = '{{name}}'; REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA orders FROM \"{{name}}\"; REVOKE USAGE ON SCHEMA orders FROM \"{{name}}\"; DROP ROLE IF EXISTS \"{{name}}\";" > /dev/null
  "$BAO" write database/roles/payment-service db_name=c39 default_ttl=1h max_ttl=24h creation_statements="$CREATE" > /dev/null
  echo "OpenBao $("$BAO" version | awk '{print $2}'), $(psql -tA -d c39 -c 'show server_version')"
  echo
  echo "== policy"
  "$BAO" policy write order-service ../src/main/resources/openbao/order-service-policy.hcl > /dev/null
  "$BAO" kv put secret/order-service/gateway apiKey="o-$(openssl rand -hex 8)" > /dev/null
  "$BAO" kv put secret/payment-service/gateway apiKey="p-$(openssl rand -hex 8)" > /dev/null
  SVC_TOKEN=$("$BAO" token create -policy=order-service -ttl=10m -field=token)
  echo "a token with only the order-service policy (src/main/resources/openbao/order-service-policy.hcl):"
  for op in "kv get secret/order-service/gateway" "kv get secret/payment-service/gateway" \
            "read database/creds/order-service" "read database/creds/payment-service" "secrets list"; do
    if BAO_TOKEN=$SVC_TOKEN "$BAO" $op > /dev/null 2>&1; then r=allowed; else r=denied; fi
    printf '  %-42s %s\n' "$op" "$r"
  done
  "$BAO" lease revoke -prefix database/creds/order-service > /dev/null
  echo
fi
for m in $MODES; do
  echo "== $m"
  java -cp $CP Measure $m
  echo
done

[ $# -gt 0 ] && exit 0
# the API checks: sparse clones of the projects at the versions used (git and github.com access)
SRC_DIR=${C39_SOURCES:-build/src}
mkdir -p "$SRC_DIR"
clone() {   # url tag dir paths...
  local url=$1 tag=$2 dir=$SRC_DIR/$3; shift 3
  if [ ! -d "$dir/.git" ]; then
    git -c advice.detachedHead=false clone -q --depth 1 --branch "$tag" --filter=blob:none --sparse "$url" "$dir"
    git -C "$dir" sparse-checkout set "$@"
  fi
}
clone https://github.com/spring-projects/spring-boot.git v4.1.1 boot core/spring-boot/src/main \
  module/spring-boot-actuator-autoconfigure/src/main module/spring-boot-jdbc/src/main platform
clone https://github.com/spring-cloud/spring-cloud-vault.git v5.0.2 scv spring-cloud-vault-config/src/main \
  spring-cloud-vault-config-databases/src/main
clone https://github.com/spring-projects/spring-vault.git 4.0.3 sv spring-vault-core/src/main/java/org/springframework/vault/core/lease
clone https://github.com/brettwooldridge/HikariCP.git HikariCP-7.0.2 hikari src/main/java
clone https://github.com/openbao/openbao.git v2.7.1 openbao internal/builtin/database/postgresql \
  internal/builtin/logical/database internal/builtin/credential/kubernetes
clone https://github.com/external-secrets/external-secrets.git v2.12.0 eso apis/externalsecrets/v1
clone https://github.com/kubernetes/kubernetes.git v1.37.1 k8s staging/src/k8s.io/apiserver/pkg/apis/apiserver/v1
echo "== apis"
./check_apis.sh "$SRC_DIR/boot" "$SRC_DIR/scv" "$SRC_DIR/sv" "$SRC_DIR/hikari" "$SRC_DIR/openbao" "$SRC_DIR/eso" "$SRC_DIR/k8s"
