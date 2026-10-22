package dev.foodfleet.ch05.ordering.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch05.catalog.CatalogApi.CatalogMenuItem;
import dev.foodfleet.ch05.catalog.CatalogApi.CatalogRestaurant;
import dev.foodfleet.ch05.ordering.application.UpstreamContractException;
import dev.foodfleet.ch05.ordering.domain.Identifiers.MenuItemId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.RestaurantId;
import dev.foodfleet.ch05.ordering.domain.Money;
import dev.foodfleet.ch05.ordering.domain.OrderableItem;
import dev.foodfleet.ch05.ordering.domain.RestaurantTerms;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The anti-corruption layer's interpretation rules, pinned one by one. */
class CatalogTranslatorTest {

    private final CatalogTranslator translator = new CatalogTranslator();

    @Test
    void translatesCatalogVocabularyIntoOrderingVocabulary() {
        CatalogMenuItem upstream = item("dum-biryani", "349.00", "IN_STOCK");

        assertThat(translator.toOrderableItem(upstream)).isEqualTo(new OrderableItem(
                new MenuItemId("dum-biryani"), new RestaurantId("rest-biryani-house"),
                "Hyderabadi Dum Biryani", Money.ofRupees("349.00"), true));
    }

    @Test
    void onlyInStockIsOrderable_unknownStatusesFailSafe() {
        assertThat(translator.toOrderableItem(item("x", "10", "OUT_OF_STOCK")).available()).isFalse();
        assertThat(translator.toOrderableItem(item("x", "10", "SEASONAL")).available()).isFalse();
        assertThat(translator.toOrderableItem(item("x", "10", "LIMITED_EDITION")).available())
                .as("a status Catalog adds next month must not become sellable by accident")
                .isFalse();
    }

    @Test
    void malformedPriceIsAContractBreach() {
        assertThatThrownBy(() -> translator.toOrderableItem(item("x", "₹349", "IN_STOCK")))
                .isInstanceOf(UpstreamContractException.class)
                .hasMessage("Catalog sent an invalid priceInr of x: '₹349'");
    }

    @Test
    void restaurantStatusBecomesAcceptingOrders() {
        CatalogRestaurant paused = new CatalogRestaurant("rest-1", "Paused Place", "PAUSED", "149.00", "5.00", "Chinese");

        assertThat(translator.toTerms(paused)).isEqualTo(new RestaurantTerms(
                new RestaurantId("rest-1"), false, Money.ofRupees("149.00"), Money.ofRupees("5.00")));
    }

    private static CatalogMenuItem item(String sku, String price, String stock) {
        return new CatalogMenuItem(sku, "rest-biryani-house", sku.equals("dum-biryani") ? "Hyderabadi Dum Biryani" : sku,
                price, stock, "N", List.of(), "https://img.example/" + sku + ".jpg");
    }
}
