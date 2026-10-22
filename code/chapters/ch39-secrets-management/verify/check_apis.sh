#!/usr/bin/env bash
# Checks each API, property and default this chapter relies on against the sources of the versions used:
# Spring Boot 4.1.1, Spring Cloud Vault 5.0.2 (Spring Cloud 2025.1.2), Spring Vault 4.0.3, HikariCP 7.0.2,
# OpenBao 2.7.1, External Secrets Operator 2.12.0 and Kubernetes 1.37.1.
# Usage: ./check_apis.sh <boot> <spring-cloud-vault> <spring-vault> <hikari> <openbao> <eso> <kubernetes>
set -uo pipefail
BOOT=$1 SCV=$2 SV=$3 HK=$4 BAO=$5 ESO=$6 K8S=$7
fail=0
check() {   # description, root, path, extended regex
  if grep -Eq -- "$4" "$2/$3" 2>/dev/null; then
    printf 'OK       %s\n' "$1"
  else
    printf 'MISSING  %s  (%s)\n' "$1" "$3"
    fail=$((fail + 1))
  fi
}
absent() {  # description, root, path, extended regex that must NOT occur (the file must exist)
  if [ -f "$2/$3" ] && ! grep -Eq -- "$4" "$2/$3"; then
    printf 'OK       %s\n' "$1"
  else
    printf 'MISSING  %s  (%s)\n' "$1" "$3"
    fail=$((fail + 1))
  fi
}
C=spring-cloud-vault-config/src/main/java/org/springframework/cloud/vault/config
L=spring-vault-core/src/main/java/org/springframework/vault/core/lease
H=src/main/java/com/zaxxer/hikari

echo "-- Spring Boot 4.1.1"
check "configtree: import reads files without ALWAYS_READ (cached)" "$BOOT" core/spring-boot/src/main/java/org/springframework/boot/context/config/ConfigTreeConfigDataLoader.java 'new ConfigTreePropertySource\(name, path, Option.AUTO_TRIM_TRAILING_NEW_LINE\)'
check "Fact: ...so content is read once, unless ALWAYS_READ"   "$BOOT" core/spring-boot/src/main/java/org/springframework/boot/env/ConfigTreePropertySource.java 'cachedContent = options.contains\(Option.ALWAYS_READ\) \? null'
check "management.endpoint.env.show-values defaults to NEVER"   "$BOOT" module/spring-boot-actuator-autoconfigure/src/main/java/org/springframework/boot/actuate/autoconfigure/env/EnvironmentEndpointProperties.java 'private Show showValues = Show.NEVER'
check "DataSourceProperties.initializeDataSourceBuilder()"      "$BOOT" module/spring-boot-jdbc/src/main/java/org/springframework/boot/jdbc/autoconfigure/DataSourceProperties.java 'public DataSourceBuilder<\?> initializeDataSourceBuilder\(\)'
check "DataSourceBuilder.type(Class)"                           "$BOOT" module/spring-boot-jdbc/src/main/java/org/springframework/boot/jdbc/DataSourceBuilder.java 'public <D extends DataSource> DataSourceBuilder<D> type\('
check "HikariCP version managed by Boot: 7.0.2"                 "$BOOT" platform/spring-boot-dependencies/build.gradle 'library\("HikariCP", "7.0.2"\)'
echo "-- Spring Cloud Vault 5.0.2"
check "spring.cloud.vault.authentication=KUBERNETES"            "$SCV" "$C/VaultProperties.java" 'APPROLE, .*KUBERNETES'
check "spring.cloud.vault.kubernetes.role / kubernetes-path"    "$SCV" "$C/VaultProperties.java" 'private String kubernetesPath = "kubernetes"'
check "...service-account-token-file default"                   "$SCV" "$C/VaultProperties.java" 'serviceAccountTokenFile = "/var/run/secrets/kubernetes.io/serviceaccount/token"'
check "spring.cloud.vault.kv: backend secret, KV v2"            "$SCV" "$C/VaultKeyValueBackendProperties.java" 'private int backendVersion = 2'
check "spring.cloud.vault.config.lifecycle.expiry-threshold"    "$SCV" "$C/VaultProperties.java" 'private Duration expiryThreshold;'
check "spring.cloud.vault.database: enabled false by default"   "$SCV" spring-cloud-vault-config-databases/src/main/java/org/springframework/cloud/vault/config/databases/VaultDatabaseProperties.java 'private boolean enabled = false'
check "Fact: database secrets are plain (non-leasing) metadata" "$SCV" spring-cloud-vault-config-databases/src/main/java/org/springframework/cloud/vault/config/databases/VaultConfigDatabaseBootstrapConfiguration.java 'return new SecretBackendMetadata\(\) \{'
check "Fact: ...which are requested as renewable, not rotating" "$SCV" "$C/VaultConfigDataLoader.java" 'return RequestedSecret.renewable\(accessor.getPath\(\)\)'
check "Fact: the SecretLeaseContainer is registered as a bean"  "$SCV" "$C/VaultConfigDataLoader.java" 'registerIfAbsent\(bootstrap, "secretLeaseContainer", SecretLeaseContainer.class'
echo "-- Spring Vault 4.0.3"
check "SecretLeaseContainer.requestRotatingSecret(String)"      "$SV" "$L/SecretLeaseContainer.java" 'public RequestedSecret requestRotatingSecret\(String path\)'
check "Fact: requested after start → started immediately"       "$SV" "$L/SecretLeaseContainer.java" 'if \(this.status == STATUS_STARTED\)'
check "addLeaseListener(LeaseListener)"                         "$SV" "$L/SecretLeaseEventPublisher.java" 'public void addLeaseListener\(LeaseListener listener\)'
check "LeaseListener.onLeaseEvent"                              "$SV" "$L/event/LeaseListener.java" 'void onLeaseEvent\(SecretLeaseEvent leaseEvent\)'
check "SecretLeaseCreatedEvent.getSecrets()"                    "$SV" "$L/event/SecretLeaseCreatedEvent.java" 'public Map<String, Object> getSecrets\(\)'
check "SecretLeaseRotatedEvent extends SecretLeaseCreatedEvent" "$SV" "$L/event/SecretLeaseRotatedEvent.java" 'class SecretLeaseRotatedEvent extends SecretLeaseCreatedEvent'
check "SecretLeaseExpiredEvent"                                 "$SV" "$L/event/SecretLeaseExpiredEvent.java" 'class SecretLeaseExpiredEvent'
check "SecretLeaseEvent.getSource() is the RequestedSecret"     "$SV" "$L/event/SecretLeaseEvent.java" 'public RequestedSecret getSource\(\)'
echo "-- HikariCP 7.0.2"
check "HikariCredentialsProvider (since 7.0.0)"                 "$HK" "$H/HikariCredentialsProvider.java" 'Credentials getCredentials\(\);'
check "HikariConfig.setCredentialsProvider"                     "$HK" "$H/HikariConfig.java" 'public void setCredentialsProvider\(HikariCredentialsProvider'
check "Fact: the provider is asked for every new connection"    "$HK" "$H/pool/PoolBase.java" 'return credentialsProvider.getCredentials\(\)'
check "HikariPoolMXBean.softEvictConnections"                   "$HK" "$H/HikariPoolMXBean.java" 'void softEvictConnections\(\);'
check "Credentials.of(user, password)"                          "$HK" "$H/util/Credentials.java" 'public static Credentials of\('
echo "-- OpenBao 2.7.1"
check "Fact: default PostgreSQL revocation drops the role"      "$BAO" internal/builtin/database/postgresql/postgresql.go 'DROP ROLE IF EXISTS %s;'
check "Fact: ...after revoking privileges and CONNECT"          "$BAO" internal/builtin/database/postgresql/postgresql.go 'REVOKE CONNECT ON DATABASE %s FROM %s;'
absent "Fact: ...and does not terminate sessions"               "$BAO" internal/builtin/database/postgresql/postgresql.go 'pg_terminate_backend'
check "database/rotate-root/:name"                              "$BAO" internal/builtin/logical/database/path_rotate_credentials.go 'Pattern: "rotate-root/"'
check "kubernetes auth role: bound_service_account_names"       "$BAO" internal/builtin/credential/kubernetes/path_role.go '"bound_service_account_names": \{'
check "kubernetes auth role: audience"                          "$BAO" internal/builtin/credential/kubernetes/path_role.go '"audience": \{'
echo "-- External Secrets Operator 2.12.0"
check "ExternalSecret refreshInterval defaults to 1h"           "$ESO" apis/externalsecrets/v1/externalsecret_types.go 'kubebuilder:default="1h0m0s"'
check "remoteRef key / property"                                "$ESO" apis/externalsecrets/v1/externalsecret_types.go 'Property string `json:"property,omitempty"`'
check "Vault provider: kubernetes auth mountPath / role"        "$ESO" apis/externalsecrets/v1/secretstore_vault_types.go 'Path string `json:"mountPath"`'
check "Vault provider: KV version"                              "$ESO" apis/externalsecrets/v1/secretstore_vault_types.go 'Version VaultKVStoreVersion `json:"version"`'
echo "-- Kubernetes 1.37.1"
check "EncryptionConfiguration providers: kms"                  "$K8S" staging/src/k8s.io/apiserver/pkg/apis/apiserver/v1/types_encryption.go 'KMS \*KMSConfiguration `json:"kms,omitempty"`'
check "KMS apiVersion (v2)"                                     "$K8S" staging/src/k8s.io/apiserver/pkg/apis/apiserver/v1/types_encryption.go 'APIVersion string `json:"apiVersion"`'
check "identity provider"                                       "$K8S" staging/src/k8s.io/apiserver/pkg/apis/apiserver/v1/types_encryption.go 'Identity \*IdentityConfiguration `json:"identity,omitempty"`'
echo
if [ "$fail" -eq 0 ]; then echo "all APIs found"; else echo "$fail missing"; exit 1; fi
