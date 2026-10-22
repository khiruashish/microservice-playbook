package dev.foodfleet.lab01;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The web layer only: controller, advice, validation and JSON, with the real repository. */
@WebMvcTest(MenuController.class)
@Import(MenuRepository.class)
class MenuControllerTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void returnsTheMenu() {
        assertThat(mvc.get().uri("/restaurants/r-1/menu"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.items.length()").isEqualTo(3);
    }

    @Test
    void filtersVegetarianDishes() {
        assertThat(mvc.get().uri("/restaurants/r-1/menu?veg=true"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.items[*].vegetarian").asArray().containsOnly(true);
    }

    @Test
    void unknownRestaurantIsAProblemDocument() {
        assertThat(mvc.get().uri("/restaurants/r-9/menu"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.type")
                .isEqualTo("https://docs.foodfleet.example/problems/restaurant-not-found");
    }

    @Test
    void invalidItemIsRejectedWithA400Problem() {
        assertThat(mvc.post().uri("/restaurants/r-1/menu")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "", "pricePaise": 0, "vegetarian": true}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void addingAnItemReturns201WithItsLocation() {
        assertThat(mvc.post().uri("/restaurants/r-1/menu")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Gobi manchurian", "pricePaise": 18900, "vegetarian": true}
                        """))
                .hasStatus(HttpStatus.CREATED)
                .headers().containsHeader("Location");
    }
}
