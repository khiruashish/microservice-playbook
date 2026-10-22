package dev.foodfleet.ch42.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch42.resilience.Deadline;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

class BudgetedRetryTest {

    static HttpRequest request(HttpMethod method) {
        HttpHeaders h = new HttpHeaders();
        h.set(Deadline.HEADER, Long.toString(System.currentTimeMillis() + 5_000));
        return new HttpRequest() {
            public HttpMethod getMethod() {
                return method;
            }

            public URI getURI() {
                return URI.create("http://restaurant-service/menu");
            }

            public HttpHeaders getHeaders() {
                return h;
            }
        };
    }

    static ClientHttpResponse status(int code) {
        return new ClientHttpResponse() {
            public java.io.InputStream getBody() {
                return java.io.InputStream.nullInputStream();
            }

            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }

            public HttpStatusCode getStatusCode() {
                return HttpStatusCode.valueOf(code);
            }

            public String getStatusText() {
                return "";
            }

            public void close() {
            }
        };
    }

    @Test
    void retriesIdempotentRequestsOnUnavailableUntilSuccess() throws IOException {
        BudgetedRetry retry = new BudgetedRetry(0.1, 3, Duration.ofMillis(1), Duration.ofMillis(2));
        AtomicInteger calls = new AtomicInteger();
        ClientHttpResponse r = retry.intercept(request(HttpMethod.GET), new byte[0],
                (req, body) -> status(calls.incrementAndGet() < 3 ? 503 : 200));
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void doesNotRetryPostsOrClientErrors() throws IOException {
        BudgetedRetry retry = new BudgetedRetry(0.1, 3, Duration.ofMillis(1), Duration.ofMillis(2));
        AtomicInteger calls = new AtomicInteger();
        retry.intercept(request(HttpMethod.POST), new byte[0], (req, body) -> status(calls.incrementAndGet() > 0 ? 503 : 200));
        assertThat(calls.get()).isEqualTo(1);
        retry.intercept(request(HttpMethod.GET), new byte[0], (req, body) -> status(calls.incrementAndGet() > 0 ? 404 : 200));
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void aDownDependencyGetsAtMostTheBudget() throws IOException {
        BudgetedRetry retry = new BudgetedRetry(0.1, 3, Duration.ZERO, Duration.ZERO);
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 1000; i++) {
            retry.intercept(request(HttpMethod.GET), new byte[0], (req, body) -> {
                calls.incrementAndGet();
                return status(503);
            });
        }
        assertThat(calls.get()).isBetween(1000, 1115);   // without the budget: 3,000
    }

    @Test
    void ioErrorsAreRethrownWhenRetriesRunOut() {
        BudgetedRetry retry = new BudgetedRetry(0.1, 2, Duration.ZERO, Duration.ZERO);
        assertThatThrownBy(() -> retry.intercept(request(HttpMethod.GET), new byte[0], (req, body) -> {
            throw new java.net.ConnectException("refused");
        })).isInstanceOf(java.net.ConnectException.class);
    }
}
