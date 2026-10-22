package dev.foodfleet.ch10.gateway;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/** Cross-cutting edge concerns, written as plain Gateway Server MVC filter functions. */
public final class EdgeFilters {

    public static final String REQUEST_ID = "X-Request-Id";

    /**
     * Headers only the platform may set. A client that sends {@code X-Customer-Id: someone-else}
     * must not be believed by the services behind the gateway, so the edge removes them.
     */
    static final List<String> INTERNAL_HEADERS = List.of("X-Customer-Id", "X-Internal-Caller", "X-Debug-Mode");

    private EdgeFilters() {
    }

    /** Strip spoofable internal headers and make sure every request carries a request id. */
    public static Function<ServerRequest, ServerRequest> edgeHygiene() {
        return request -> ServerRequest.from(request)
                .headers(headers -> {
                    INTERNAL_HEADERS.forEach(headers::remove);
                    String id = headers.getFirst(REQUEST_ID);
                    if (id == null || id.isBlank() || id.length() > 64) {
                        headers.set(REQUEST_ID, UUID.randomUUID().toString());
                    }
                })
                .build();
    }

    /**
     * Rejects with 429 when the client's bucket is empty. Until authentication arrives (chapter 36)
     * the key is the client address; afterwards it is the token's subject.
     */
    public static HandlerFilterFunction<ServerResponse, ServerResponse> rateLimit(
            TokenBucketRateLimiter limiter, Function<ServerRequest, String> keyResolver) {
        return (request, next) -> {
            TokenBucketRateLimiter.Decision decision = limiter.tryAcquire(keyResolver.apply(request));
            if (decision.allowed()) {
                return next.handle(request);
            }
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many orders from this device in a short time. Please wait a moment.");
            problem.setTitle("Slow down");
            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(retryAfterSeconds(decision.retryAfter())))
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        };
    }

    static String clientAddress(ServerRequest request) {
        return request.servletRequest().getRemoteAddr();
    }

    static long retryAfterSeconds(Duration retryAfter) {
        return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }
}
