package dev.foodfleet.ch01.modulith.order.internal.remote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.foodfleet.ch01.modulith.order.KitchenUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/** Fast unit test of the HTTP adapter: no Spring context, the HTTP layer is mocked at the wire level. */
class RemoteRestaurantAvailabilityTest {

    private MockRestServiceServer kitchenService;
    private RemoteRestaurantAvailability availability;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://kitchen-service");
        kitchenService = MockRestServiceServer.bindTo(builder).build();
        KitchenAvailabilityClient client = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(builder.build()))
                .build()
                .createClient(KitchenAvailabilityClient.class);
        availability = new RemoteRestaurantAvailability(client);
    }

    @Test
    void acceptingKitchen() {
        expectAvailabilityCall("rest-1")
                .andRespond(withSuccess("""
                        {"restaurantId": "rest-1", "accepting": true, "activeTickets": 3}
                        """, MediaType.APPLICATION_JSON));

        assertThat(availability.canAccept("rest-1")).isTrue();
        kitchenService.verify();
    }

    @Test
    void unknownRestaurantIsABusinessNo() {
        expectAvailabilityCall("rest-404").andRespond(withResourceNotFound());

        assertThat(availability.canAccept("rest-404")).isFalse();
    }

    @Test
    void serverErrorIsAnInfrastructureFailure() {
        expectAvailabilityCall("rest-500").andRespond(withServerError());

        assertThatThrownBy(() -> availability.canAccept("rest-500"))
                .isInstanceOf(KitchenUnavailableException.class);
    }

    private org.springframework.test.web.client.ResponseActions expectAvailabilityCall(String restaurantId) {
        return kitchenService
                .expect(requestTo("http://kitchen-service/api/v1/kitchens/" + restaurantId + "/availability"))
                .andExpect(method(HttpMethod.GET));
    }
}
