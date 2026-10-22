package dev.foodfleet.ch10.bff.api;

import dev.foodfleet.ch10.bff.tracking.TrackingCard;
import dev.foodfleet.ch10.bff.tracking.TrackingScreenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * One endpoint per screen, versioned by path because app releases pin the URL (v1 = app 5.x).
 * Reached through the gateway: GET /bff/mobile/v1/orders/{id}/tracking.
 */
@RestController
@RequestMapping("/bff/mobile/v1")
class TrackingController {

    private final TrackingScreenService screens;

    TrackingController(TrackingScreenService screens) {
        this.screens = screens;
    }

    @GetMapping("/orders/{orderId}/tracking")
    TrackingCard tracking(@PathVariable String orderId) {
        return screens.trackingCard(orderId);
    }

    @ExceptionHandler(HttpClientErrorException.NotFound.class)
    ProblemDetail orderNotFound() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "We couldn't find this order.");
        problem.setTitle("Order not found");
        return problem;
    }

    @ExceptionHandler(RestClientException.class)
    ProblemDetail orderingUnavailable() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Order details are temporarily unavailable. Pull down to refresh.");
        problem.setTitle("Temporarily unavailable");
        return problem;
    }
}
