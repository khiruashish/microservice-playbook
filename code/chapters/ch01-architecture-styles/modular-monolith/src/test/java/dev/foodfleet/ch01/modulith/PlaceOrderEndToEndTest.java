package dev.foodfleet.ch01.modulith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import dev.foodfleet.ch01.modulith.kitchen.KitchenOperations;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Whole application, all modules, real HTTP layer (MockMvc), real database (H2). */
@SpringBootTest
@AutoConfigureMockMvc
class PlaceOrderEndToEndTest {

    private final MockMvcTester mvc;
    private final KitchenOperations kitchen;

    PlaceOrderEndToEndTest(@Autowired MockMvcTester mvc, @Autowired KitchenOperations kitchen) {
        this.mvc = mvc;
        this.kitchen = kitchen;
    }

    @Test
    void orderIsAcceptedImmediatelyAndKitchenCatchesUpAsynchronously() throws Exception {
        MvcTestResult result = mvc.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson("rest-e2e-1"))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.status").isEqualTo("PLACED");

        UUID orderId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.orderId"));

        // The HTTP response did not wait for the kitchen. The ticket appears shortly after commit.
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(kitchen.ticketFor(orderId)).isPresent());

        assertThat(mvc.get().uri("/api/v1/orders/{id}", orderId))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.totalPaise").isEqualTo(76_700);
    }

    @Test
    void pausedKitchenReturnsConflictProblemDetail() {
        assertThat(mvc.post().uri("/api/v1/kitchens/{id}/pause", "rest-e2e-2")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(mvc.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson("rest-e2e-2")))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.type")
                .isEqualTo("https://foodfleet.dev/problems/restaurant-unavailable");
    }

    @Test
    void kitchenAvailabilityEndpointMatchesExtractedServiceContract() {
        assertThat(mvc.get().uri("/api/v1/kitchens/{id}/availability", "rest-e2e-3"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"restaurantId": "rest-e2e-3", "accepting": true, "activeTickets": 0}
                        """);
    }

    private static String orderJson(String restaurantId) {
        return """
                {
                  "customerId": "cust-42",
                  "restaurantId": "%s",
                  "lines": [
                    {"itemId": "dum-biryani", "name": "Hyderabadi Dum Biryani", "quantity": 2, "unitPricePaise": 34900},
                    {"itemId": "raita", "name": "Boondi Raita", "quantity": 1, "unitPricePaise": 6900}
                  ]
                }
                """.formatted(restaurantId);
    }
}
