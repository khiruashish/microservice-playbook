#!/usr/bin/env bash
# Checks what chapter 77 relies on against its sources (sparse clones made by build-libs.sh): the MCP specification
# 2026-07-28 and Spring AI 2.0.1. Files are read with whitespace collapsed. Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex (over the file with runs of whitespace collapsed to one space)
  if tr -s ' \t\n' '   ' < "$2" 2>/dev/null | grep -Eq -- "$3"; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}
S=$SRC/mcp/schema/2026-07-28/schema.ts
D=$SRC/mcp/docs/specification/2026-07-28

echo "-- MCP 2026-07-28"
check "latest protocol version 2026-07-28"                     $S 'export const LATEST_PROTOCOL_VERSION = "2026-07-28";'
check "stateless: no initialize handshake"                     $D/changelog.mdx 'Make MCP stateless: remove the `initialize`/`notifications/initialized` handshake'
check "every result carries resultType"                        $S 'Servers implementing this protocol version MUST include this field\. .* resultType: ResultType;'
check "list results carry ttlMs and cacheScope"                $S 'export interface CacheableResult extends Result \{ .*ttlMs: number;.*cacheScope: "public" \| "private";'
check "Mcp-Method on every request, Mcp-Name on tools/call"    $D/basic/transports/streamable-http.mdx '\| `Mcp-Method` \| `method` \| All requests \| \| `Mcp-Name` \| `params\.name` or `params\.uri` \| `tools/call`'
check "header mismatch: 400 and -32020"                        $S 'export const HEADER_MISMATCH = -32020;'
check "unsupported version: -32022"                            $S 'export const UNSUPPORTED_PROTOCOL_VERSION = -32022;'
check "tool errors go in the result (isError), not protocol"   $S 'Any errors that originate from the tool SHOULD be reported inside the result \* object, with `isError` set to true, _not_ as an MCP protocol-level error'

echo "-- Spring AI 2.0.1"
check "@Tool with name and description"                        $SRC/spring-ai/spring-ai-model/src/main/java/org/springframework/ai/tool/annotation/Tool.java 'public @interface Tool \{.*String name\(\) default "";.*String description\(\) default "";'
check "MCP server transports: SSE, STREAMABLE (default), STATELESS" $SRC/spring-ai/auto-configurations/mcp/spring-ai-autoconfigure-mcp-server-common/src/main/java/org/springframework/ai/mcp/server/common/autoconfigure/properties/McpServerProperties.java 'private ServerProtocol protocol = ServerProtocol\.STREAMABLE;.*SSE, STREAMABLE, STATELESS'

echo "apis: $((10 - fail)) of 10 found"
exit $fail
