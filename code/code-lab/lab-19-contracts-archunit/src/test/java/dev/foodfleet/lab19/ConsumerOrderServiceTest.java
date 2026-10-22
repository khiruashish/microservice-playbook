package dev.foodfleet.lab19;

import static org.assertj.core.api.Assertions.assertThat;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import dev.foodfleet.lab19.order.MenuPriceClient;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.RestClient;

/**
 * order-service's contract with menu-service: what it sends, and only the parts of each answer it reads. The price is
 * matched by format, not by value, so a new price on the menu doesn't break the contract; a price in paise does.
 * Chapter 63: type matchers plus formats caught 7 of 9 breaking changes with no false alarms; recorded examples
 * caught the same 7 but also flagged all 5 ordinary menu updates.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "menu-service")
class ConsumerOrderServiceTest {

    @Pact(consumer = "order-service")
    V4Pact priceOfAnAvailableItem(PactDslWithProvider builder) {
        return builder
                .given("restaurant 7 has paneer-tikka at 129.50")
                .uponReceiving("the price of an available item")
                .path("/restaurants/7/items/paneer-tikka/price").method("GET")
                .willRespondWith().status(200)
                .body(new PactDslJsonBody()
                        .stringMatcher("price", "^\\d+\\.\\d{2}$", "129.50")
                        .booleanType("available", true))
                .toPact(V4Pact.class);
    }

    @Pact(consumer = "order-service")
    V4Pact priceOfAnUnknownItem(PactDslWithProvider builder) {
        return builder
                .given("restaurant 7 has no item called biryani")
                .uponReceiving("the price of an unknown item")
                .path("/restaurants/7/items/biryani/price").method("GET")
                .willRespondWith().status(404)
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "priceOfAnAvailableItem")
    void readsThePrice(MockServer menuService) {
        MenuPriceClient client = new MenuPriceClient(RestClient.create(menuService.getUrl()));

        assertThat(client.price("7", "paneer-tikka"))
                .contains(new MenuPriceClient.Price(new BigDecimal("129.50"), true));
    }

    @Test
    @PactTestFor(pactMethod = "priceOfAnUnknownItem")
    void anUnknownItemHasNoPrice(MockServer menuService) {
        MenuPriceClient client = new MenuPriceClient(RestClient.create(menuService.getUrl()));

        assertThat(client.price("7", "biryani")).isEmpty();
    }
}
