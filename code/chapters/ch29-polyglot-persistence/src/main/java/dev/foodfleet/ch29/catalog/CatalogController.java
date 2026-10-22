package dev.foodfleet.ch29.catalog;

import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController {

    private final MenuCards cards;
    private final DishSearch search;
    private final CatalogService catalog;

    public CatalogController(MenuCards cards, DishSearch search, CatalogService catalog) {
        this.cards = cards;
        this.search = search;
        this.catalog = catalog;
    }

    /** The stored document, sent as it is; its version is the ETag, so an unchanged menu costs a 304. */
    @GetMapping("/restaurants/{id}/menu")
    ResponseEntity<String> menu(@PathVariable long id,
                                @RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch) {
        MenuCards.Card card = cards.find(id).orElseThrow(() -> new NoSuchElementException("no menu for restaurant " + id));
        String etag = "\"" + card.version() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).contentType(MediaType.APPLICATION_JSON).body(card.json());
    }

    @GetMapping("/dishes")
    List<DishSearch.Hit> dishes(@RequestParam("q") String q, @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return search.search(q, Math.min(Math.max(limit, 1), 50));
    }

    @PutMapping("/items/{id}")
    CatalogService.Changed change(@PathVariable long id, @RequestBody ItemChange change) {
        return catalog.change(id, change);
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
