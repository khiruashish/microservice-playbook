# Chapter 63 — Contract Testing

Code and measurements for [chapter 63](../../../docs/testing/ch63-contract-testing.md).

| Path | What |
|---|---|
| `src/main/java/.../contract/` | `Contract` (Pact v3 files), `Matching` (Pact's response matching: type with cascade, `min`, regex on the value's string form, unexpected keys allowed), `Verifier` (provider verification), `StubServer` (the consumer's stub) |
| `src/main/java/.../openapi/` | `SchemaDiff`: oasdiff 1.34's rule ids and levels for paths, parameters, status codes and response schemas |
| `src/main/java/.../menu/`, `.../consumer/` | menu-service (the provider) and two consumers: order-service's `OrderMenuClient`, app-bff's `AppMenuClient` |
| `src/main/resources/openapi/` | menu-service's OpenAPI 3.1 document |
| `src/test/resources/contracts/` | the two consumers' Pact files |
| `src/test/java/` | 9 tests: consumer tests against stubs, provider verification, matching and diff |
| `verify/` | `build-libs.sh`, `run.sh`, `check_apis.sh` (18 checks), `changes.tsv` (12 API changes), `data-changes.tsv` (5 data changes), `tools/` (`Lab`, `RunSuites`) |

```bash
JAVA_HOME=/path/to/jdk-25 verify/run.sh   # under a minute
```

## Findings (JDK 25.0.4, two runs, identical apart from timings)

12 changes to menu-service; 9 broke a consumer when both clients ran against all of its data.

| Check | Caught (of 9) | False alarms (of 3) | Time per change |
|---|---|---|---|
| OpenAPI diff (oasdiff's ERR rules) | 5 | 2 (unused field removed; price as a number) | 1.1–1.8 ms |
| order-service's contract | 6 | 0 | 16.0–16.5 ms |
| app-bff's contract | 5 | 0 | 9.3–9.4 ms |
| both contracts | 7 | 0 | |
| E2E on the test restaurant | 7 (the same 7) | 0 | 19.2–20.4 ms |

- Only the diff caught the new enum value (it appeared only on a restaurant no test used). Nothing caught pagination (only matters above 20 items).
- Prices moved to paise passed the diff (no schema change) and were caught by the contracts' two-decimal format.

The same contracts written three ways, against the 12 API changes and 5 harmless data changes:

| Contracts | Caught (of 9) | Harmless API changes flagged | Data changes flagged |
|---|---|---|---|
| examples recorded from the provider, no matchers | 7 | 2 of 3 | 5 of 5 |
| type matchers only | 6 | 1 of 3 | 0 of 5 |
| type matchers + formats | 7 | 0 of 3 | 0 of 5 |

**tests**: 9 of 9 passed. **apis**: 18 of 18 found in Pact JVM 4.7.5, Spring Cloud Contract 5.0.3, the Pact Broker 2.122.0 and oasdiff 1.34.0.

## Notes

- Pact JVM, Spring Cloud Contract and oasdiff weren't run: Maven Central and the Go toolchain weren't available. `Matching` and `SchemaDiff` reproduce the rules the experiment depends on, and `check_apis.sh` checks those rules in the tools' sources. The contracts are valid Pact v3 files.
- Each provider version runs in its own JVM, compiled from `MenuService.java` with the change applied; it is warmed up before the checks, so timings don't include its JIT.
- Provider states are covered by the provider's fixed data rather than state set-up calls.
