#!/usr/bin/env bash
# Chapter 40 measurements.
#   ./run.sh                          everything
#   ./run.sh limiters fairness cors   only what needs nothing but a JDK (21+)
# The other modes need:
#   redos     also python3 and node, for the same pattern in their regex engines
#   redis     redis-server on the PATH (run.sh starts one on 127.0.0.1:6390 and stops it)
#   tenancy   PostgreSQL 16 reachable as a superuser with psql (PGHOST, PGPORT, PGUSER; trust or .pgpass), accepting
#             SCRAM-SHA-256 logins over TCP at APP_PGHOST:APP_PGPORT (default 127.0.0.1:$PGPORT). Creates and
#             drops the database c40 and the roles c40_owner and c40_app.
#   browser   python3 with Playwright and Chromium (PLAYWRIGHT_BROWSERS_PATH), and keytool
#   waf       Go 1.25+ and network access to fetch Coraza 3.8 (or WAF_BIN=<a build of waf/>); coreruleset v4.30.0
#             is fetched with git
set -euo pipefail
cd "$(dirname "$0")"
SRC=../src/main/java
rm -rf build/main build/tools && mkdir -p build/main build/tools
javac -d build/main $(find $SRC -name '*.java' -not -path '*/web/*')
cp -r ../src/main/resources/* build/main/
javac -d build/tools -cp build/main tools/*.java
CP=build/main:build/tools
MODES=${*:-limiters fairness redos cors redis tenancy browser waf}
SRC_DIR=${C40_SOURCES:-build/src}
mkdir -p "$SRC_DIR"
SCG_PATHS="/spring-cloud-gateway-server-webflux/src/main/resources/META-INF/scripts/
  /spring-cloud-gateway-server-webflux/src/main/java/org/springframework/cloud/gateway/filter/ratelimit/
  /spring-cloud-gateway-server-webflux/src/main/java/org/springframework/cloud/gateway/filter/factory/RequestRateLimiterGatewayFilterFactory.java
  /spring-cloud-gateway-server-webflux/src/main/java/org/springframework/cloud/gateway/config/GatewayProperties.java"
clone() {   # url tag dir paths...
  local url=$1 tag=$2 dir=$SRC_DIR/$3; shift 3
  if [ ! -d "$dir/.git" ]; then
    git -c advice.detachedHead=false clone -q --depth 1 --branch "$tag" --filter=blob:none --sparse "$url" "$dir"
    git -C "$dir" sparse-checkout set --no-cone "$@"
  fi
}

for m in $MODES; do
  echo "== $m"
  case $m in
    limiters|fairness|redos|cors)
      java -Xmx2g -Dstdout.encoding=UTF-8 -cp $CP Measure "$m"
      if [ "$m" = redos ]; then
        python3 tools/redos.py
        node tools/redos.js
      fi ;;
    redis)
      clone https://github.com/spring-cloud/spring-cloud-gateway.git v5.0.2 scg $SCG_PATHS
      redis-server --port 6390 --save '' --appendonly no --daemonize yes --logfile "$PWD/build/redis.log"
      trap 'redis-cli -p 6390 shutdown nosave >/dev/null 2>&1 || true' EXIT
      for _ in $(seq 50); do redis-cli -p 6390 ping >/dev/null 2>&1 && break; sleep 0.1; done
      SCG_SCRIPT=$SRC_DIR/scg/spring-cloud-gateway-server-webflux/src/main/resources/META-INF/scripts/request_rate_limiter.lua \
        REDIS_PORT=6390 java -Dstdout.encoding=UTF-8 -cp $CP Measure redis
      redis-cli -p 6390 shutdown nosave >/dev/null 2>&1 || true
      trap - EXIT ;;
    tenancy)
      export APP_PGHOST=${APP_PGHOST:-127.0.0.1} APP_PGPORT=${APP_PGPORT:-${PGPORT:-5432}}
      export C40_OWNER_PASSWORD="owner-$(openssl rand -hex 12)" C40_APP_PASSWORD="app-$(openssl rand -hex 12)"
      export PGOPTIONS='-c client_min_messages=warning'
      psql -q -v ON_ERROR_STOP=1 -d postgres <<SQL
DROP DATABASE IF EXISTS c40 WITH (FORCE);
DROP ROLE IF EXISTS c40_app;
DROP ROLE IF EXISTS c40_owner;
CREATE ROLE c40_owner LOGIN PASSWORD '$C40_OWNER_PASSWORD';
CREATE ROLE c40_app LOGIN NOBYPASSRLS PASSWORD '$C40_APP_PASSWORD';
CREATE DATABASE c40 OWNER c40_owner;
SQL
      PGPASSWORD=$C40_OWNER_PASSWORD psql -q -v ON_ERROR_STOP=1 -h "$APP_PGHOST" -p "$APP_PGPORT" -U c40_owner -d c40 <<'SQL'
-- the same 200,000 orders four times: without RLS, with RLS not forced, with RLS (nullif policy), strict policy
CREATE TABLE orders_plain (
    tenant_id text NOT NULL, id bigint NOT NULL, customer_id text NOT NULL, total_paise bigint NOT NULL,
    created_at timestamptz NOT NULL, PRIMARY KEY (tenant_id, id));
INSERT INTO orders_plain
SELECT 'tenant-' || lpad(t::text, 2, '0'), g, 'cust-' || (g % 100 + 1), 10000 + (g * 7919) % 90000,
       timestamptz '2026-01-01' + (g * 37 % 86400) * interval '1 minute'
FROM generate_series(1, 50) t, generate_series(1, 4000) g;
CREATE INDEX ON orders_plain (tenant_id, customer_id, created_at);
CREATE TABLE orders (LIKE orders_plain INCLUDING ALL);
INSERT INTO orders SELECT * FROM orders_plain;
CREATE TABLE orders_noforce (LIKE orders_plain INCLUDING ALL);
INSERT INTO orders_noforce SELECT * FROM orders_plain;
CREATE TABLE orders_strict (LIKE orders_plain INCLUDING ALL);
INSERT INTO orders_strict SELECT * FROM orders_plain;
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON orders
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));
ALTER TABLE orders_noforce ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON orders_noforce
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''));
ALTER TABLE orders_strict ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders_strict FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON orders_strict USING (tenant_id = current_setting('app.tenant_id'));
GRANT SELECT, INSERT, UPDATE, DELETE ON orders, orders_plain, orders_noforce, orders_strict TO c40_app;
ANALYZE orders_plain, orders, orders_noforce, orders_strict;
SQL
      echo "PostgreSQL $(psql -tA -d c40 -c 'show server_version')"
      java -Dstdout.encoding=UTF-8 -cp $CP Measure tenancy
      psql -q -d postgres -c 'DROP DATABASE c40 WITH (FORCE)' -c 'DROP ROLE c40_app' -c 'DROP ROLE c40_owner' ;;
    browser)
      rm -f build/lab.p12
      keytool -genkeypair -keystore build/lab.p12 -storetype PKCS12 -storepass changeit -alias lab -keyalg EC \
        -dname CN=foodfleet.test -ext san=dns:foodfleet.test,dns:www.foodfleet.test,dns:reviews.foodfleet.test,dns:evil.test \
        -validity 2 2>/dev/null
      java -cp $CP BrowserLab build/lab.p12 8443 > build/lab.log 2>&1 &
      LAB=$!
      trap 'kill $LAB 2>/dev/null || true' EXIT
      for _ in $(seq 50); do grep -q started build/lab.log 2>/dev/null && break; sleep 0.1; done
      python3 tools/browser.py 8443
      kill $LAB; trap - EXIT ;;
    waf)
      clone https://github.com/coreruleset/coreruleset.git v4.30.0 crs /rules/ /crs-setup.conf.example
      if [ -n "${WAF_BIN:-}" ]; then cp "$WAF_BIN" build/waf; else (cd waf && go mod tidy >/dev/null && go build -o ../build/waf .); fi
      R=../src/main/resources/waf
      echo "-- OWASP CRS 4.30.0 as shipped"
      build/waf -crs "$SRC_DIR/crs" -corpus waf/corpus.jsonl
      echo "-- with FoodFleet's API settings (resources/waf/api-setup.conf)"
      build/waf -crs "$SRC_DIR/crs" -corpus waf/corpus.jsonl -extra "$R/api-setup.conf"
      echo "-- plus the rule exclusions for free-text fields (resources/waf/exclusions.conf)"
      build/waf -crs "$SRC_DIR/crs" -corpus waf/corpus.jsonl -extra "$R/api-setup.conf,$R/exclusions.conf" -v ;;
    *) echo "unknown mode $m"; exit 1 ;;
  esac
  echo
done

[ $# -gt 0 ] && exit 0
# the API checks: sparse clones of the projects at the versions used (git and github.com access)
clone https://github.com/spring-projects/spring-boot.git v4.1.1 boot /module/spring-boot-tomcat/src/main/ \
  /module/spring-boot-web-server/src/main/ /module/spring-boot-jackson/src/main/ /module/spring-boot-webmvc/src/main/ \
  /module/spring-boot-security-oauth2-resource-server/src/main/ /module/spring-boot-data-redis/src/main/ \
  /module/spring-boot-servlet/src/main/ /gradle.properties /starter/
clone https://github.com/spring-projects/spring-framework.git v7.0.9 sf /spring-web/src/main/java/org/springframework/web/cors/ \
  /spring-web/src/main/java/org/springframework/http/ProblemDetail.java
clone https://github.com/spring-projects/spring-security.git 7.1.1 ss /config/src/main/java/org/springframework/security/config/annotation/web/ \
  /web/src/main/java/org/springframework/security/web/csrf/
clone https://github.com/spring-cloud/spring-cloud-gateway.git v5.0.2 scg $SCG_PATHS
clone https://github.com/FasterXML/jackson-core.git jackson-core-3.1.5 jcore /src/main/java/tools/jackson/core/StreamReadConstraints.java
clone https://github.com/FasterXML/jackson-databind.git jackson-databind-3.1.5 jdb /src/main/java/tools/jackson/databind/DeserializationFeature.java
clone https://github.com/coreruleset/coreruleset.git v4.30.0 crs /rules/ /crs-setup.conf.example
echo "== apis"
./check_apis.sh "$SRC_DIR/boot" "$SRC_DIR/sf" "$SRC_DIR/ss" "$SRC_DIR/scg" "$SRC_DIR/jcore" "$SRC_DIR/jdb" "$SRC_DIR/crs"
