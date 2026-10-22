#!/usr/bin/env bash
# FoodFleet's OpenBao (or HashiCorp Vault: the same commands with `vault`) for order-service, run by the platform team.
# Requires BAO_ADDR and an admin token; DB_ADMIN_PASSWORD from the bootstrap secret (rotated right after, below).
set -euo pipefail

# 1. services log in with their Kubernetes service account: no secret has to be delivered to the pod for this
bao auth enable kubernetes
bao write auth/kubernetes/config kubernetes_host="https://kubernetes.default.svc:443"
bao write auth/kubernetes/role/order-service \
  bound_service_account_names=order-service bound_service_account_namespaces=orders \
  audience=openbao token_policies=order-service token_ttl=1h

# 2. what order-service may read
bao policy write order-service order-service-policy.hcl
bao secrets enable -path=secret -version=2 kv

# 3. dynamic PostgreSQL credentials: a new database role per pod, valid 1 h, renewable up to 24 h
bao secrets enable database
bao write database/config/orders-db plugin_name=postgresql-database-plugin \
  connection_url="postgresql://{{username}}:{{password}}@orders-db.orders.svc:5432/orders?sslmode=verify-full" \
  username=openbao_admin password="$DB_ADMIN_PASSWORD" allowed_roles=order-service
bao write -force database/rotate-root/orders-db          # now only OpenBao knows the admin password
bao write database/roles/order-service db_name=orders-db default_ttl=1h max_ttl=24h \
  creation_statements=@creation.sql revocation_statements=@revocation.sql
