package dev.foodfleet.ch01.modulith;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch01.modulith.order.RestaurantAvailability;
import dev.foodfleet.ch01.modulith.order.internal.remote.RemoteRestaurantAvailability;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Same application, kitchen switched to "remote", and kitchen-service deliberately NOT running.
 * This shows the new failure mode a network boundary introduces: the order module now has to
 * handle "I couldn't ask", which the in-process version could never encounter.
 */
@SpringBootTest(properties = {
        "foodfleet.kitchen.mode=remote",
        "spring.http.serviceclient.kitchen.base-url=http://127.0.0.1:1", // nothing listens on port 1
        "spring.http.serviceclient.kitchen.connect-timeout=200ms",
        "spring.http.serviceclient.kitchen.read-timeout=500ms"
})
@AutoConfigureMockMvc
class RemoteKitchenModeTest {

    private final RestaurantAvailability availability;
    private final MockMvcTester mvc;

    RemoteKitchenModeTest(@Autowired RestaurantAvailability availability, @Autowired MockMvcTester mvc) {
        this.availability = availability;
        this.mvc = mvc;
    }

    @Test
    void orderModuleUsesTheHttpAdapter() {
        assertThat(availability).isInstanceOf(RemoteRestaurantAvailability.class);
    }

    @Test
    void unreachableKitchenServiceYields503WithRetryAfter() {
        assertThat(mvc.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerId": "cust-1", "restaurantId": "rest-remote-1",
                         "lines": [{"itemId": "dosa", "name": "Masala Dosa", "quantity": 1, "unitPricePaise": 12000}]}
                        """))
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .hasHeader(HttpHeaders.RETRY_AFTER, "5")
                .bodyJson().extractingPath("$.title").isEqualTo("Kitchen service unavailable");
    }
}
