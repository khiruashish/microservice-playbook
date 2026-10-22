package dev.foodfleet.lab13;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MenuController {

    private final MenuCards cards;

    MenuController(MenuCards cards) {
        this.cards = cards;
    }

    @GetMapping(path = "/menus/{restaurantId}", produces = MediaType.APPLICATION_JSON_VALUE)
    String menu(@PathVariable String restaurantId) {
        return cards.card(restaurantId);
    }

    @Configuration
    static class Wiring {

        /** The database stand-in: in lab 02's service this is the JPA query that builds the card. */
        @Bean
        MenuCards menuCards(SharedCache sharedCache) {
            return new MenuCards(sharedCache, id -> "{\"restaurantId\":\"" + id + "\",\"items\":[]}", Clock.systemUTC());
        }
    }
}
