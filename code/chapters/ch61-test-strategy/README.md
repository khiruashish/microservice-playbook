# Chapter 61 — Test Pyramid & Honeycomb

Code and measurements for [chapter 61](../../../docs/testing/ch61-test-strategy.md).

| Path | What |
|---|---|
| `src/main/java/.../order/` | order-service in plain Java: `Pricing` (FIRST20 at 20% capped at Rs 100, 5% GST on the discounted amount, Rs 199 minimum, half-even rounding), `OrderService`, `JdbcOrderRepository` (idempotent insert with `ON CONFLICT`, header and lines in one transaction), `HttpMenuClient`, `OrderApi` (201/200/400/404) |
| `src/main/resources/db/schema.sql` | orders and order lines |
| `src/test/java/` | five suites by JUnit tag: `unit` (8 tests), `h2` (4), `postgres` (4, the same tests as `h2`), `contract` (3), `component` (4); `MenuStub` serves `contracts/menu-service.json` |
| `verify/` | `build-libs.sh` (JUnit 6.1.3, the PostgreSQL driver and H2 from Maven Central; source clones), `run.sh`, `check_apis.sh` (11 checks), `mutants.tsv` (20 bugs), `tools/` (`RunSuites`, `Mutate`) |

```bash
JAVA_HOME=/path/to/jdk-25 verify/run.sh          # starts postgres:16 in docker; or set FF_PG_URL
JAVA_HOME=/path/to/jdk-25 verify/run.sh suites   # the five suites on correct code only
mvn test -Dgroups=unit                           # with Maven: one suite by tag
```

## Findings (JDK 25.0.4, JUnit 6.1.3, PostgreSQL 16.13, H2 2.5.252, two runs)

Each of 20 bugs was compiled into the service in turn, and all five suites ran against it. The matrix was the same in both runs.

| Suite | Tests | Bugs caught (of 20) | Which | Run time |
|---|---|---|---|---|
| unit | 8 | 7 | all 7 logic bugs, nothing else | 868–897 ms |
| h2 | 4 | 0 | fails 3 of 4 tests on correct code | 386–443 ms |
| postgres | 4 | 6 | 6 of 7 SQL and transaction bugs | 657–1,032 ms |
| contract | 3 | 3 | all 3 client bugs | 387–391 ms |
| component | 4 | 12 | 3 logic, 4 SQL, 3 API, 2 client | 494–999 ms |

- All five together caught 19. Unit + postgres + contract (a pyramid without component tests) caught 16; component + postgres + contract (a honeycomb without unit tests) 15.
- Only unit tests caught the coupon cap, rounding, the minimum's boundary and sold-out items. Only component tests caught the three API bugs.
- No suite caught R7 (`ORDER BY` removed from the lines query): tests checked how many lines came back, not their order.
- H2 in PostgreSQL mode doesn't parse `ON CONFLICT (column) DO NOTHING`, only `ON CONFLICT DO NOTHING`; the H2 suite's one passing test passed because the insert failed with a syntax error, which its `assertThrows` accepted.

**apis**: 11 of 11 found in JUnit 6.1.3, Spring Boot 4.1.1 and H2 2.5.252.

## Notes

- Maven Central wasn't reachable where these runs were made; JUnit 6.1.3, the PostgreSQL driver 42.7.14 (with its shaded SCRAM client) and H2 2.5.252 were built from their release tags instead. `build-libs.sh` fetches the same versions from Maven Central.
- PostgreSQL ran on the same VM, not in a container; each test class gets its own database, dropped by `Mutate` after each bug.
- The first version of `Mutate` counted a bug as caught by any suite with a failing test, so the H2 suite, failing on correct code, appeared to catch every bug. It now compares with the suite's failures on correct code.
- The suites are plain JUnit 6 without Spring; with Spring Boot, the same layers map to `@SpringBootTest` with a random port (component), `@JdbcTest` or `@DataJpaTest` on a Testcontainers database (repository), and `@WebMvcTest` slices (chapter 62).
