package dev.foodfleet.ch42.web;

import dev.foodfleet.ch42.resilience.Backoff;
import dev.foodfleet.ch42.resilience.Deadline;
import dev.foodfleet.ch42.resilience.RetryBudget;
import dev.foodfleet.ch42.resilience.RetryPolicy;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Retries for one downstream service, shared by every caller in this instance: only idempotent requests (or ones
 * carrying an Idempotency-Key), only on 502/503/504 and I/O errors, with jittered exponential backoff, within the
 * request's deadline, and within a budget of {@code ratio} retries per request. One budget per downstream service:
 * when restaurant-service is down, the budget runs dry after a handful of retries instead of tripling its load.
 */
public final class BudgetedRetry implements ClientHttpRequestInterceptor {

    private static final Set<HttpMethod> IDEMPOTENT = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.PUT, HttpMethod.DELETE,
            HttpMethod.OPTIONS);
    private static final Set<Integer> RETRYABLE = Set.of(502, 503, 504);

    private final RetryBudget budget;
    private final RetryPolicy policy;

    public BudgetedRetry(double ratio, int maxAttempts, Duration base, Duration max) {
        this.budget = new RetryBudget(ratio, 10);
        this.policy = new RetryPolicy(maxAttempts, Backoff.fullJitter(base, max), budget);
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        budget.onRequest();
        boolean retryable = IDEMPOTENT.contains(request.getMethod()) || request.getHeaders().containsHeader("Idempotency-Key");
        Deadline deadline = Deadline.parse(request.getHeaders().getFirst(Deadline.HEADER))
                .orElse(Deadline.after(Duration.ofSeconds(10), System.currentTimeMillis()));
        int attempts = 0;
        while (true) {
            attempts++;
            ClientHttpResponse response = null;
            IOException failure = null;
            try {
                response = execution.execute(request, body);
                if (!RETRYABLE.contains(response.getStatusCode().value())) {
                    return response;
                }
            } catch (IOException e) {
                failure = e;
            }
            Optional<Duration> wait = retryable
                    ? policy.next(attempts, deadline.remaining(System.currentTimeMillis()), ThreadLocalRandom.current())
                    : Optional.empty();
            if (wait.isEmpty()) {
                if (failure != null) {
                    throw failure;
                }
                return response;
            }
            if (response != null) {
                response.close();
            }
            try {
                Thread.sleep(wait.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("interrupted while backing off");
            }
        }
    }
}
