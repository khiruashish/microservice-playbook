# Chapter 8 code — Spring Boot 4 essentials: FoodFleet eta-service

Docs: `docs/foundations/ch08-spring-boot-4-essentials.md`

"When will my food arrive?" One small service that uses the Boot 4 / Framework 7 features a
microservice needs every day:

| Feature | Where |
|---|---|
| Modular starters (`webmvc`, `restclient`, per-technology test starters) | `pom.xml` |
| HTTP service clients, one group per downstream service | `client/KitchenClient`, `client/DispatchClient`, `config/*ClientConfiguration`, `application.yml` |
| Virtual threads + parallel fan-out with plain blocking code | `client/EtaService`, `spring.threads.virtual.enabled` |
| `@Retryable` / `@ConcurrencyLimit` (Framework 7, no extra library) | `client/DispatchGateway` |
| Built-in API versioning, deprecation headers (RFC 9745 / 8594) | `api/EtaController`, `config/WebConfiguration` |
| Jackson 3: `@JacksonComponent`, `JsonMapperBuilderCustomizer`, new defaults | `api/EtaWindowJacksonComponent`, `config/WebConfiguration`, `Jackson3DefaultsTest` |
| JSpecify null-safety (`@NullMarked`, `@Nullable`) | `package-info.java`, `EtaCalculator`, `DispatchClient` |
| `MockMvcTester`, `RestTestClient`, `@MockitoBean` | `EtaControllerTest` |
| Real clients against stub servers | `EtaServiceIntegrationTest` |

```bash
cd code
mvn -B -pl chapters/ch08-spring-boot-4 -am verify
mvn -pl chapters/ch08-spring-boot-4 spring-boot:run        # port 8088; needs kitchen (8091) and dispatch (8092)

curl -s localhost:8088/api/orders/o-1/eta -i                        # v1 + Deprecation/Sunset/Link headers
curl -s localhost:8088/api/orders/o-1/eta -H 'API-Version: 2.0'     # v2: window, ISO instant, breakdown
```
