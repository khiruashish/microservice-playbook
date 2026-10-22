package dev.foodfleet.ch21.api;

import java.net.URI;
import org.springframework.http.HttpStatus;

/**
 * The API's own error types. Each has a stable {@code type} URI (RFC 9457) that clients switch on,
 * and that resolves to documentation. Titles are stable per type; details are per occurrence.
 */
public enum ApiProblem {

    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "invalid-cursor", "Invalid page cursor"),
    INVALID_LIMIT(HttpStatus.BAD_REQUEST, "invalid-limit", "Invalid page size"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "order-not-found", "Order not found"),
    PRECONDITION_REQUIRED(HttpStatus.PRECONDITION_REQUIRED, "precondition-required", "If-Match header required"),
    STALE_VERSION(HttpStatus.PRECONDITION_FAILED, "stale-version", "Order was changed by someone else");

    static final String BASE = "https://docs.foodfleet.example/problems/";

    final HttpStatus status;
    final URI type;
    final String title;

    ApiProblem(HttpStatus status, String slug, String title) {
        this.status = status;
        this.type = URI.create(BASE + slug);
        this.title = title;
    }
}
