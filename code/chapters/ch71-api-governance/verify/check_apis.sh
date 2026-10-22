#!/usr/bin/env bash
# Checks what chapter 71 relies on against its sources (sparse clones made by build-libs.sh): Spectral's recommended
# OpenAPI ruleset (spectral-rulesets 1.16.0), Zalando's RESTful API guidelines (commit 19a1905), Backstage 1.55.3's
# entity schemas and Spring Framework 7.0.9's deprecation handler. Files are read with whitespace collapsed.
# Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex (over the file with runs of whitespace collapsed to one space)
  if tr -s ' \t\n' '   ' < "$2" 2>/dev/null | grep -Eq -- "$3"; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
O=$SRC/spectral/packages/rulesets/src/oas/index.ts
Z=$SRC/zalando/chapters
B=$SRC/backstage/packages/catalog-model/src/schema/kinds
H=$SRC/spring/spring-web/src/main/java/org/springframework/web/accept/StandardApiVersionDeprecationHandler.java

echo "-- Spectral spectral:oas (the ten rules used, all recommended; two are errors, the rest warnings)"
for r in operation-operationId operation-description operation-tags operation-tag-defined info-contact operation-success-response path-keys-no-trailing-slash oas3-api-servers; do
  check "$r is recommended" $O "'$r': \{ (description|message): '[^']*', recommended: true,"
done
check "operation-operationId-unique: error"   $O "'operation-operationId-unique': \{ description: '[^']*', recommended: true, severity: 0,"
check "path-params: error"                    $O "'path-params': \{ description: '[^']*', message: '\{\{error\}\}', severity: 0, recommended: true,"

echo "-- Zalando RESTful API guidelines (the house rules' sources)"
check "[129] MUST use kebab-case for path segments"   $Z/urls.adoc '\[#129\] == \{MUST\} use kebab-case for path segments'
check "[141] MUST keep URLs verb-free"                $Z/urls.adoc '\[#141\] == \{MUST\} keep URLs verb-free'
check "[134] MUST pluralize resource names"           $Z/urls.adoc '\[#134\] == \{MUST\} pluralize resource names'
check "[115] MUST not use URL versioning"             $Z/compatibility.adoc '\[#115\] == \{MUST\} not use URL versioning'
check "[176] MUST support problem JSON"               $Z/http-status-codes-and-errors.adoc '\[#176\] == \{MUST\} support problem JSON'
check "[160] SHOULD prefer cursor-based pagination"   $Z/pagination.adoc '\[#160\] == \{SHOULD\} prefer cursor-based pagination'
check "[118] MUST property names must be snake_case"  $Z/json-guidelines.adoc '\[#118\] == \{MUST\} property names must be snake_case'
check "[173] MUST use the common money object"        $Z/json-guidelines.adoc '\[#173\] == \{MUST\} use the common money object'
check "[187] MUST reflect deprecation in API specifications" $Z/deprecation.adoc '\[#187\] == \{MUST\} reflect deprecation in API specifications'
check "[188] MUST monitor usage of deprecated API"    $Z/deprecation.adoc '\[#188\] == \{MUST\} monitor usage of deprecated API'
check "[189] SHOULD add Deprecation and Sunset header" $Z/deprecation.adoc '\[#189\] == \{SHOULD\} add `Deprecation` and `Sunset` header'

echo "-- Backstage catalog model"
check "Component requires type, lifecycle, owner"     $B/Component.v1alpha1.schema.json '"required": \["type", "lifecycle", "owner"\]'
check "API requires type, lifecycle, owner, definition" $B/API.v1alpha1.schema.json '"required": \["type", "lifecycle", "owner", "definition"\]'

echo "-- Spring Framework 7.0.9 deprecation headers"
check "Deprecation: @<epoch seconds> (RFC 9745)"      $H '"@" \+ deprecationDate\.toInstant\(\)\.getEpochSecond\(\)'
check "Sunset formatted with RFC_1123_DATE_TIME"      $H 'sunsetDate\.format\(DateTimeFormatter\.RFC_1123_DATE_TIME\)'
check "Link with rel=\"deprecation\""                 $H 'rel=\\"deprecation\\"; type=\\"%s\\"'

echo
if [ $fail -eq 0 ]; then echo "all checks passed"; else echo "$fail checks failed"; exit 1; fi
