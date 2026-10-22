package dev.foodfleet.ch10.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where the menu route's circuit breaker forwards when menu-service is failing. A fast, honest 503
 * beats making every customer wait for timeouts. (A CDN-cached menu is even better; see the CDN page in Components.)
 */
@RestController
class FallbackController {

    @RequestMapping("/fallback/menus")
    ResponseEntity<ProblemDetail> menus() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Menus are taking longer than usual to load. Please try again in a few seconds.");
        problem.setTitle("Menu temporarily unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "5").body(problem);
    }
}
