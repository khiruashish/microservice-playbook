#!/usr/bin/env bash
# Checks the test-framework behaviours chapter 62 relies on against their sources (sparse clones made by build-libs.sh):
# Spring Boot 4.1.1, Spring Framework 7.0.9 (spring-test), Testcontainers 2.0.5, JUnit 6.1.3, H2 2.5.252.
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex
  if grep -Eq -- "$3" "$2" 2>/dev/null; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
B=$SRC/boot; F=$SRC/sf/spring-test/src/main/java/org/springframework/test/context; T=$SRC/tc; J=$SRC/junit; H=$SRC/h2

echo "-- Spring Boot 4.1.1 test slices"
JT=$B/module/spring-boot-jdbc-test/src/main/java/org/springframework/boot/jdbc/test/autoconfigure
check "@JdbcTest: every test runs in a transaction rolled back at the end" $JT/JdbcTest.java '^@Transactional$'
check "@JdbcTest: replaces the DataSource with an embedded one"          $JT/JdbcTest.java '^@AutoConfigureTestDatabase$'
check "@DataJpaTest: transactional too"                                   $B/module/spring-boot-data-jpa-test/src/main/java/org/springframework/boot/data/jpa/test/autoconfigure/DataJpaTest.java '^@Transactional$'
check "@AutoConfigureTestDatabase: replace defaults to NON_TEST"         $JT/AutoConfigureTestDatabase.java 'Replace replace\(\) default Replace\.NON_TEST;'
check "...a @ServiceConnection container counts as a test database"     $JT/AutoConfigureTestDatabase.java '@code @ServiceConnection\} annotated Testcontainers databases'
check "@WebMvcTest exists in the webmvc-test module"                      $B/module/spring-boot-webmvc-test/src/main/java/org/springframework/boot/webmvc/test/autoconfigure/WebMvcTest.java 'public @interface WebMvcTest'
check "@ServiceConnection: connection details from a container"          $B/core/spring-boot-testcontainers/src/main/java/org/springframework/boot/testcontainers/service/connection/ServiceConnection.java 'public @interface ServiceConnection'
check "JDBC containers map to JdbcConnectionDetails"                      $B/module/spring-boot-jdbc/src/main/java/org/springframework/boot/jdbc/testcontainers/JdbcContainerConnectionDetailsFactory.java 'JdbcConnectionDetails'

echo "-- Spring Framework 7.0.9 test context cache"
check "context cache holds 32 contexts by default"                        $F/cache/ContextCache.java 'int DEFAULT_MAX_CONTEXT_CACHE_SIZE = 32;'
check "...set with spring.test.context.cache.maxSize"                      $F/cache/ContextCache.java '"spring\.test\.context\.cache\.maxSize"'
check "inactive contexts are paused, ON_CONTEXT_SWITCH by default"         $F/cache/ContextCacheUtils.java 'return PauseMode\.ON_CONTEXT_SWITCH;'

echo "-- Testcontainers 2.0.5"
PG=$T/modules/postgresql/src/main/java/org/testcontainers/postgresql/PostgreSQLContainer.java
check "PostgreSQLContainer runs postgres with fsync=off"                  $PG 'setCommand\("postgres", "-c", FSYNC_OFF_OPTION\)'
check "...and waits for the 'ready' log line twice (initdb restarts it)"  $PG 'database system is ready to accept connections\.\*\\\\s", 2\)'
check "@Container on a static field: one container for the class's tests" $T/modules/junit-jupiter/src/main/java/org/testcontainers/junit/jupiter/TestcontainersExtension.java 'return isContainer\(\)\.and\(ModifierSupport::isStatic\);'
check "reuse needs testcontainers.reuse.enable=true"                      $T/core/src/main/java/org/testcontainers/utility/TestcontainersConfiguration.java '"testcontainers\.reuse\.enable", "false"'
check "...and finds the container by a hash of its create command"        $T/core/src/main/java/org/testcontainers/containers/GenericContainer.java 'containerId = findContainerForReuse\(hash\)'
check "Ryuk removes leftover containers unless TESTCONTAINERS_RYUK_DISABLED" $T/core/src/main/java/org/testcontainers/utility/ResourceReaper.java 'TESTCONTAINERS_RYUK_DISABLED'

echo "-- JUnit 6.1.3"
check "store values that are AutoCloseable are closed with their context" $J/junit-jupiter-api/src/main/java/org/junit/jupiter/api/extension/ExtensionContext.java 'that are instances of \{@link AutoCloseable\} are notified by invoking'
check "Store.CloseableResource is deprecated for AutoCloseable"           $J/junit-jupiter-api/src/main/java/org/junit/jupiter/api/extension/ExtensionContext.java 'Please extend \{@code AutoCloseable\} directly'

echo "-- H2 2.5.252"
check "PostgreSQL mode: ON CONFLICT DO NOTHING, without a conflict target" $H/h2/src/main/org/h2/command/Parser.java 'readIfCompat\(ON, "CONFLICT", "DO", "NOTHING"\)'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
