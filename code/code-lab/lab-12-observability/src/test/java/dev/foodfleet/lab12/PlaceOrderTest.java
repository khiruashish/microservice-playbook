package dev.foodfleet.lab12;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The observation's contract: its name, which values are metric tags, which are only on the span, and errors. */
class PlaceOrderTest {

    final TestObservationRegistry observations = TestObservationRegistry.create();
    final RestClient.Builder builder = RestClient.builder().baseUrl("http://menu");
    final MockRestServiceServer menu = MockRestServiceServer.bindTo(builder).build();
    @SuppressWarnings("unchecked")
    final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    final PlaceOrder placeOrder = new PlaceOrder(observations, builder.build(), kafka);

    @Test
    void anOrderIsOneObservationWithBoundedTags() {
        menu.expect(requestTo("http://menu/menus/r-42")).andRespond(withSuccess());

        placeOrder.place(new PlaceOrder.Request("o-1", "r-42", "UPI"));

        assertThat(observations).hasObservationWithNameEqualTo(PlaceOrder.OBSERVATION).that()
                .hasLowCardinalityKeyValue("payment.method", "UPI")
                .hasHighCardinalityKeyValue("order.id", "o-1")
                .doesNotHaveLowCardinalityKeyValueWithKey("order.id")
                .doesNotHaveLowCardinalityKeyValueWithKey("restaurant.id");
        verify(kafka).send(eq(PlaceOrder.TOPIC), eq("o-1"), anyString());
    }

    @Test
    void anUnknownPaymentMethodIsNotANewTagValue() {
        menu.expect(requestTo("http://menu/menus/r-42")).andRespond(withSuccess());

        placeOrder.place(new PlaceOrder.Request("o-2", "r-42", "upi; drop table"));

        assertThat(observations).hasObservationWithNameEqualTo(PlaceOrder.OBSERVATION).that()
                .hasLowCardinalityKeyValue("payment.method", "OTHER");
    }

    @Test
    void aFailingMenuMarksTheObservationAsAnError() {
        menu.expect(requestTo("http://menu/menus/r-42")).andRespond(withServerError());

        assertThatThrownBy(() -> placeOrder.place(new PlaceOrder.Request("o-3", "r-42", "CARD")));

        assertThat(observations).hasObservationWithNameEqualTo(PlaceOrder.OBSERVATION).that().hasError();
    }
}
