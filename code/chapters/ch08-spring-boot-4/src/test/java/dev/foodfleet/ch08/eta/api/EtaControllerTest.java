package dev.foodfleet.ch08.eta.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import dev.foodfleet.ch08.eta.client.EtaService;
import dev.foodfleet.ch08.eta.client.KitchenUnavailableException;
import dev.foodfleet.ch08.eta.config.WebConfiguration;
import dev.foodfleet.ch08.eta.domain.EtaCalculator;
import dev.foodfleet.ch08.eta.domain.EtaCalculator.RiderLeg;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.client.RestTestClient;

/** The HTTP contract of both API versions, pinned without starting a server. */
@WebMvcTest(EtaController.class)
@Import({WebConfiguration.class, EtaWindowJacksonComponent.class})
class EtaControllerTest {

    @MockitoBean
    EtaService etas;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void etaIs27Minutes() {
        given(etas.etaFor("o-1")).willReturn(new EtaCalculator()
                .calculate(12, new RiderLeg("rider-7", 8, 15), 20, Instant.parse("2026-10-01T08:00:00Z")));
    }

    @Test
    void noVersionHeaderGetsV1_markedDeprecated() {
        assertThat(mvc.get().uri("/api/orders/o-1/eta"))
                .hasStatusOk()
                .containsHeader("Deprecation")
                .containsHeader("Sunset")
                .containsHeader("Link")
                .bodyJson().isStrictlyEqualTo("{\"orderId\":\"o-1\",\"etaMinutes\":27}");
    }

    @Test
    void version2GetsTheWindowAndAnIsoInstant() {
        assertThat(mvc.get().uri("/api/orders/o-1/eta").header("API-Version", "2.0"))
                .hasStatusOk()
                .doesNotContainHeader("Deprecation")
                .bodyJson().isStrictlyEqualTo("""
                        {
                          "orderId": "o-1",
                          "window": {"from": 24, "to": 30, "display": "24–30 min"},
                          "estimatedArrival": "2026-10-01T08:27:00Z",
                          "breakdown": {"prepMinutes": 12, "waitForRiderMinutes": 0, "travelMinutes": 15},
                          "degraded": false
                        }
                        """);
    }

    @Test
    void unsupportedVersionIsABadRequest() {
        assertThat(mvc.get().uri("/api/orders/o-1/eta").header("API-Version", "3.0"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void kitchenOutageIsA503ProblemWithRetryAfter_viaRestTestClient() {
        given(etas.etaFor("o-2")).willThrow(new KitchenUnavailableException("o-2", new RuntimeException("timeout")));

        // Spring Framework 7's RestTestClient: the RestClient-style API, here bound to MockMvc
        RestTestClient client = RestTestClient.bindTo(mockMvc).build();
        client.get().uri("/api/orders/o-2/eta").header("API-Version", "2.0")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().valueEquals("Retry-After", "5")
                .expectBody().jsonPath("$.title").isEqualTo("ETA temporarily unavailable");
    }
}
