package dev.foodfleet.lab01;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/restaurants/{restaurantId}/menu")
public class MenuController {

    public record Menu(String restaurantId, List<MenuItem> items) {
    }

    private final MenuRepository menus;

    public MenuController(MenuRepository menus) {
        this.menus = menus;
    }

    @GetMapping
    public Menu menu(@PathVariable String restaurantId, @RequestParam(defaultValue = "false") boolean veg) {
        List<MenuItem> items = menus.menu(restaurantId).orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
        return new Menu(restaurantId, veg ? items.stream().filter(MenuItem::vegetarian).toList() : items);
    }

    @PostMapping
    public ResponseEntity<MenuItem> add(@PathVariable String restaurantId, @Valid @RequestBody NewMenuItem item) {
        MenuItem created = menus.add(restaurantId, item);
        return ResponseEntity.created(URI.create("/restaurants/%s/menu/%s".formatted(restaurantId, created.id()))).body(created);
    }
}
