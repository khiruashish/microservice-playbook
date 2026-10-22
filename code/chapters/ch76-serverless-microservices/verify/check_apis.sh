#!/usr/bin/env bash
# Checks what chapter 76 relies on against its sources (sparse clones made by build-libs.sh): Spring Cloud Function
# 5.0.4 and Knative Serving 1.23.0's autoscaler defaults. Lambda's and Fargate's prices and billing rules are from AWS's
# pages, cited in the chapter. Files are read with whitespace collapsed. Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex (over the file with runs of whitespace collapsed to one space)
  if tr -s ' \t\n' '   ' < "$2" 2>/dev/null | grep -Eq -- "$3"; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
F=$SRC/spring-cloud-function/spring-cloud-function-context/src/main/java/org/springframework/cloud/function/context
K=$SRC/knative/config/core/configmaps/autoscaler.yaml

echo "-- Spring Cloud Function"
check "the function to run: spring.cloud.function.definition"  $F/FunctionProperties.java 'PREFIX = "spring.cloud.function";.*FUNCTION_DEFINITION = PREFIX \+ ".definition";'
check "FunctionCatalog looks functions up by definition"       $F/FunctionCatalog.java 'default <T> T lookup\(String functionDefinition\)'
check "the AWS adapter is a Lambda RequestStreamHandler"       $SRC/spring-cloud-function/spring-cloud-function-adapters/spring-cloud-function-adapter-aws/src/main/java/org/springframework/cloud/function/adapter/aws/FunctionInvoker.java 'public class FunctionInvoker implements RequestStreamHandler'

echo "-- Knative Serving autoscaler defaults"
check "scale to zero is on"                                    $K 'enable-scale-to-zero: "true"'
check "stable window 60 s"                                     $K 'stable-window: "60s"'
check "target 70% of an instance's capacity"                   $K 'container-concurrency-target-percentage: "70"'
check "grace period before the last pod goes: 30 s"            $K 'scale-to-zero-grace-period: "30s"'

echo "apis: $((7 - fail)) of 7 found"
exit $fail
