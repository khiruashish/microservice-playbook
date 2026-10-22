package dev.foodfleet.ch01.layered;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch01.layered.repository.OrderRepository;
import dev.foodfleet.ch01.layered.service.KitchenService;
import dev.foodfleet.ch01.layered.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc // Boot 4: @SpringBootTest no longer configures MockMvc on its own
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PlaceOrderFlowTest {

    private static final String ORDER_JSON = """
            {
              "customerId": "cust-42",
              "restaurantId": "rest-biryani-house",
              "lines": [
                {"itemId": "dum-biryani", "name": "Hyderabadi Dum Biryani", "quantity": 2, "unitPricePaise": 34900},
                {"itemId": "raita", "name": "Boondi Raita", "quantity": 1, "unitPricePaise": 6900}
              ]
            }
            """;

    private final MockMvcTester mvc;
    private final KitchenService kitchen;
    private final NotificationService notifications;
    private final OrderRepository orders;

    PlaceOrderFlowTest(@Autowired MockMvcTester mvc, @Autowired KitchenService kitchen,
                       @Autowired NotificationService notifications, @Autowired OrderRepository orders) {
        this.mvc = mvc;
        this.kitchen = kitchen;
        this.notifications = notifications;
        this.orders = orders;
    }

    @Test
    void placesOrderAndSynchronouslyTouchesKitchenAndNotifications() {
        assertThat(mvc.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.status").isEqualTo("CONFIRMED");

        assertThat(kitchen.ordersWithTickets()).hasSize(1);
        assertThat(notifications.sentMessages()).singleElement().asString().contains("₹767.00"); // 2 × 349.00 + 69.00
    }

    @Test
    void kitchenFailureFailsTheWholeOrder_temporalCoupling() {
        kitchen.pause("rest-biryani-house");

        assertThat(mvc.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.title").isEqualTo("Restaurant unavailable");

        assertThat(orders.count()).isZero();
        assertThat(notifications.sentMessages()).isEmpty();
    }

    @Test
    void rejectsInvalidRequestWithProblemDetails() {
        assertThat(mvc.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerId": "", "restaurantId": "r1", "lines": []}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
