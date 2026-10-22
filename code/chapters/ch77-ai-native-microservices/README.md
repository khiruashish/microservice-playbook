# Chapter 77 — AI-Native Microservices

Code and measurements for [chapter 77](../../../docs/governance-org/ch77-ai-native-microservices.md). The model provider
is a stub with stated behaviour; the gateway, the caches and the MCP server are the code under test.

| Path | What |
|---|---|
| `src/main/java/.../provider/` | `StubModel`: streamed tokens after a time to first token, an 8-call in-flight limit (429, retry-after), 529 overloads, stalled streams |
| `src/main/java/.../gateway/` | `LlmGateway` (first-token, idle and total deadlines; retries with backoff and retry-after; global and per-feature limits), `ResponseCache` (exact, normalised, similarity) |
| `src/main/java/.../mcp/` | `McpServer`: MCP 2026-07-28 on stateless Streamable HTTP; header and version checks; identity from the bearer token (or, to measure the difference, from tool arguments) |
| `src/main/resources/support-questions.tsv` | 27 support intents, 83 phrasings |
| `src/test/java/` | `UnitTest` (3, tag `unit`) |
| `verify/` | `build-libs.sh`, `run.sh`, `check_apis.sh` (10 checks), `tools/` (`Lab`, `RunSuites`) |

```bash
JAVA_HOME=/path/to/jdk-25 verify/run.sh   # about 13 minutes
```

## Findings (JDK 25.0.4, two runs; cache and MCP authorization identical)

**deadlines** (150 calls; 2% 529, 3% stalled streams): total 30 s, no retry: 143/150, p99 30.0 s; total 10 s, 1 retry: 146/150, p99 10.0 s; first token 3 s + idle 1 s + total 20 s, 2 retries: 145–147/150, p99 5.1–5.3 s, failures were 429s while abandoned streams still held provider slots.

**quota** (chat 1.5/s beside a 160-call batch, provider limit 8): chat alone p50 1.9 s; no control 19–32/60 chats succeeded, 507–520 rejections; one global queue all succeeded at p50 9.3 s; batch capped at 4: p50 2.0 s, p99 6.1–6.6 s, batch 159 s (95 s uncapped).

**cache** (5,000 questions): exact 93.3% reused, normalised 97.0%, similarity 97.9–98.5% with 0–33 wrong answers (from cosine 0.70: "vegetarian" questions answered as "non vegetarian").

**mcp**: discover, list and call return resultType complete (list results with ttlMs and cacheScope); missing or mismatched headers 400 / -32020; old version -32022; no token 401. tools/call 0.40 ms p50. Injected calls for another customer's order: 30/30 served with identity from arguments, 0/30 with identity from the token.

**tests**: unit 3/3. **apis**: 10 of 10 found in the MCP specification 2026-07-28 and Spring AI 2.0.1.
