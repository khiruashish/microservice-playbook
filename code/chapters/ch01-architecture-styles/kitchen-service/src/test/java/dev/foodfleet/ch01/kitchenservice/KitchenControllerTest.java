package dev.foodfleet.ch01.kitchenservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(KitchenController.class) // Boot 4: slice annotations moved to org.springframework.boot.webmvc.test.autoconfigure
@Import(KitchenAvailabilityService.class)
@TestPropertySource(properties = {
        "foodfleet.kitchen.max-active-tickets=1",
        "foodfleet.kitchen.known-restaurants=rest-a,rest-b,rest-c"
})
class KitchenControllerTest {

    private final MockMvcTester mvc;
    private final KitchenAvailabilityService service;

    KitchenControllerTest(@Autowired MockMvcTester mvc, @Autowired KitchenAvailabilityService service) {
        this.mvc = mvc;
        this.service = service;
    }

    @Test
    void servesTheSameContractTheMonolithModuleServed() {
        assertThat(mvc.get().uri("/api/v1/kitchens/{id}/availability", "rest-a"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .isStrictlyEqualTo("""
                        {"restaurantId": "rest-a", "accepting": true, "activeTickets": 0}
                        """);
    }

    @Test
    void unknownRestaurantIs404() {
        assertThat(mvc.get().uri("/api/v1/kitchens/{id}/availability", "rest-nope"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void pauseAndCapacityStopAcceptance() {
        assertThat(mvc.post().uri("/api/v1/kitchens/{id}/pause", "rest-b")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/v1/kitchens/{id}/availability", "rest-b"))
                .bodyJson().extractingPath("$.accepting").isEqualTo(false);

        service.ticketOpened("rest-c"); // capacity is 1 in this test
        assertThat(mvc.get().uri("/api/v1/kitchens/{id}/availability", "rest-c"))
                .bodyJson().extractingPath("$.accepting").isEqualTo(false);
    }
}
