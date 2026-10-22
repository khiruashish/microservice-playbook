package dev.foodfleet.lab19;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import au.com.dius.pact.provider.spring.spring7.PactVerificationSpring7Provider;
import au.com.dius.pact.provider.spring.spring7.Spring7MockMvcTestTarget;
import dev.foodfleet.lab19.menu.api.PriceController;
import dev.foodfleet.lab19.menu.application.PriceQuery;
import dev.foodfleet.lab19.menu.domain.MenuItem;
import dev.foodfleet.lab19.menu.infrastructure.InMemoryMenuItems;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * menu-service verifies every consumer's contract in its own build: one test per interaction, run against the real
 * controller through MockMvc. A change that breaks order-service fails here, in menu-service's pull request.
 * In FoodFleet the contracts come from a Pact Broker (@PactBroker) and can-i-deploy gates each release (ADR-063).
 */
@WebMvcTest(PriceController.class)
@Import({PriceQuery.class, InMemoryMenuItems.class})
@Provider("menu-service")
@PactFolder("target/pacts")
class ProviderMenuServiceTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    InMemoryMenuItems items;

    @BeforeEach
    void target(PactVerificationContext context) {
        context.setTarget(new Spring7MockMvcTestTarget(mockMvc));
    }

    @TestTemplate
    @ExtendWith(PactVerificationSpring7Provider.class)
    void verify(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("restaurant 7 has paneer-tikka at 129.50")
    void paneerTikka() {
        items.clear();
        items.save(new MenuItem("7", "paneer-tikka", new BigDecimal("129.50"), true));
    }

    @State("restaurant 7 has no item called biryani")
    void noBiryani() {
        items.clear();
    }
}
