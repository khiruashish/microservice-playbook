package dev.foodfleet.ch02.menu.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import dev.foodfleet.ch02.menu.browse.MenuQueries;
import dev.foodfleet.ch02.menu.catalog.MenuCatalog;
import dev.foodfleet.ch02.menu.pricing.PriceSource;
import dev.foodfleet.ch02.menu.pricing.SurgePricing;
import dev.foodfleet.ch02.menu.pricing.SurgeQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** API-first: the HTTP contract of each version is pinned by tests, independently of the domain. */
@WebMvcTest(MenuController.class)
@Import({MenuQueries.class, MenuCatalog.class})
class MenuControllerTest {

    @MockitoBean
    SurgePricing pricing;

    private final MockMvcTester mvc;

    MenuControllerTest(@Autowired MockMvcTester mvc) {
        this.mvc = mvc;
    }

    @BeforeEach
    void surgeIsOneAndAQuarter() {
        given(pricing.quote("BLR-KORAMANGALA"))
                .willReturn(new SurgeQuote("BLR-KORAMANGALA", 12_500, PriceSource.LIVE));
    }

    @Test
    void clientsWithoutAVersionHeaderGetV1_withDeprecationSignals() {
        assertThat(mvc.get().uri("/api/menus/{id}", "rest-biryani-house"))
                .hasStatusOk()
                .hasHeader("Deprecation", MenuController.DEPRECATION)
                .hasHeader("Sunset", MenuController.SUNSET)
                .bodyJson()
                .isStrictlyEqualTo("""
                        {
                          "restaurantId": "rest-biryani-house",
                          "restaurantName": "Biryani House",
                          "items": [
                            {"itemId": "dum-biryani", "name": "Hyderabadi Dum Biryani", "pricePaise": 43625},
                            {"itemId": "veg-biryani", "name": "Veg Dum Biryani", "pricePaise": 34875},
                            {"itemId": "raita", "name": "Boondi Raita", "pricePaise": 8625}
                          ]
                        }
                        """);
    }

    @Test
    void v2AddsFieldsWithoutChangingV1() {
        assertThat(mvc.get().uri("/api/menus/{id}", "rest-biryani-house").header("API-Version", "2.0"))
                .hasStatusOk()
                .doesNotContainHeader("Deprecation")
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "zone": "BLR-KORAMANGALA",
                          "pricing": {"multiplierBps": 12500, "source": "LIVE"},
                          "items": [
                            {"itemId": "dum-biryani", "veg": false, "available": true,
                             "basePricePaise": 34900, "currentPricePaise": 43625},
                            {"itemId": "veg-biryani", "veg": true},
                            {"itemId": "raita", "veg": true},
                            {"itemId": "double-ka-meetha", "available": false}
                          ]
                        }
                        """);
    }

    @Test
    void unsupportedVersionIsRejected() {
        assertThat(mvc.get().uri("/api/menus/{id}", "rest-biryani-house").header("API-Version", "3.0"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownRestaurantIsAProblemDetail() {
        assertThat(mvc.get().uri("/api/menus/{id}", "rest-nowhere").header("API-Version", "2.0"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.title").isEqualTo("Restaurant not found");
    }
}
