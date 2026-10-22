#!/usr/bin/env bash
# Prepares chapter 77: JUnit 6 (chapter 61's build-libs.sh) and sparse clones of the MCP specification (2026-07-28)
# and Spring AI 2.0.1 for check_apis.sh. Needs git.
set -euo pipefail
cd "$(dirname "$0")"
SRC=${C77_SOURCES:-build/src}
mkdir -p "$SRC" build
C61_SOURCES="$(cd ../../ch61-test-strategy/verify && pwd)/build/src" ../../ch61-test-strategy/verify/build-libs.sh > /dev/null
ln -sfn "$(cd ../../ch61-test-strategy/verify/build/lib && pwd)" build/lib
clone() {   # repo, tag, dir, paths...
  local repo=$1 tag=$2 dir=$3; shift 3
  if [ ! -d "$dir" ]; then
    git clone -q --depth 1 --filter=blob:none --sparse --branch "$tag" "$repo" "$dir" 2>/dev/null
    (cd "$dir" && git sparse-checkout set --no-cone "$@")
  fi
}
clone https://github.com/modelcontextprotocol/modelcontextprotocol 2026-07-28 "$SRC/mcp" /schema/2026-07-28/schema.ts \
  /docs/specification/2026-07-28/changelog.mdx /docs/specification/2026-07-28/basic/transports/streamable-http.mdx
clone https://github.com/spring-projects/spring-ai v2.0.1 "$SRC/spring-ai" /spring-ai-model/src/main/java/org/springframework/ai/tool/annotation/Tool.java \
  /auto-configurations/mcp/spring-ai-autoconfigure-mcp-server-common/src/main/java/org/springframework/ai/mcp/server/common/autoconfigure/properties/McpServerProperties.java
