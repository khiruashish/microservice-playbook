package dev.foodfleet.ch02.menu.api;

import dev.foodfleet.ch02.menu.api.MenuResponses.MenuV1;
import dev.foodfleet.ch02.menu.api.MenuResponses.MenuV2;
import dev.foodfleet.ch02.menu.browse.MenuQueries;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One path, two versions, selected by the {@code API-Version} header (Spring Framework 7 API versioning,
 * configured by {@code spring.mvc.apiversion.*}). Requests without the header get the default (1.0),
 * so clients written before versioning existed keep working.
 */
@RestController
@RequestMapping("/api/menus")
class MenuController {

    /** RFC 9745 Deprecation (structured date: 2026-10-01T00:00:00Z) and RFC 8594 Sunset. */
    static final String DEPRECATION = "@1790812800";
    static final String SUNSET = "Wed, 31 Mar 2027 00:00:00 GMT";

    private final MenuQueries menus;

    MenuController(MenuQueries menus) {
        this.menus = menus;
    }

    @GetMapping(path = "/{restaurantId}", version = "1.0")
    ResponseEntity<MenuV1> menuV1(@PathVariable String restaurantId) {
        return ResponseEntity.ok()
                .header("Deprecation", DEPRECATION)
                .header("Sunset", SUNSET)
                .header("Link", "</api/menus/" + restaurantId + ">; rel=\"successor-version\"; version=\"2.0\"")
                .body(MenuV1.from(menus.pricedMenu(restaurantId)));
    }

    @GetMapping(path = "/{restaurantId}", version = "2.0")
    MenuV2 menuV2(@PathVariable String restaurantId) {
        return MenuV2.from(menus.pricedMenu(restaurantId));
    }
}
