package dev.foodfleet.ch42.web;

import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * The same ideas with Spring Framework 7's annotations, and the defaults to change. {@code @ConcurrencyLimit}
 * blocks callers by default (an unbounded queue in disguise); REJECT turns overload into a fast failure.
 * {@code @Retryable} defaults to 3 retries one second apart with no jitter and no budget: synchronized retries that
 * multiply at every layer that uses it. Chapter 43 covers both in depth.
 */
@Service
class MenuService {

    private final RestClient restaurants;

    MenuService(RestClient restaurantClient) {
        this.restaurants = restaurantClient;
    }

    @ConcurrencyLimit(limit = 64, policy = ConcurrencyLimit.ThrottlePolicy.REJECT)
    @Retryable(maxRetries = 2, delay = 100, multiplier = 2, maxDelay = 1000, jitter = 100, includes = java.io.IOException.class)
    String menu(String restaurantId) {
        return restaurants.get().uri("/restaurants/{id}/menu", restaurantId).retrieve().body(String.class);
    }
}
