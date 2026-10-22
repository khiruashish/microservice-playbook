# What order-service may do in OpenBao/Vault, and nothing else: read its own KV secrets, get its own database
# credentials, renew and revoke its own leases. Other services' paths are simply not listed (deny by default).
path "secret/data/order-service/*" {
  capabilities = ["read"]
}

path "secret/metadata/order-service/*" {
  capabilities = ["read", "list"]
}

path "database/creds/order-service" {
  capabilities = ["read"]
}

path "sys/leases/renew" {
  capabilities = ["update"]
}

path "sys/leases/revoke" {
  capabilities = ["update"]
}
