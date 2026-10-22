package dev.foodfleet.ch51.rules;

import dev.foodfleet.ch51.rules.PrometheusSloRules.Spec;
import dev.foodfleet.ch51.slo.Slo;

import java.time.Duration;

/**
 * FoodFleet's checkout SLOs, measured at order-service from Spring Boot's {@code http.server.requests} timer
 * (Prometheus names: {@code http_server_requests_seconds_count} and {@code _bucket}). Running {@link #main} prints the
 * rule file kept in {@code src/main/resources/prometheus/checkout-slo.rules.yml}; a test keeps the two in step.
 */
public final class CheckoutSlos {

    private CheckoutSlos() {
    }

    static final String CHECKOUT = "job=\"order-service\", uri=\"/orders\", method=\"POST\"";
    static final String RUNBOOK = "https://runbooks.foodfleet.internal/order-service/checkout-slo";

    /** 99.9% of checkout requests don't fail with a server error, over 30 days. Client errors (4xx) aren't the service's fault. */
    public static final Spec AVAILABILITY = new Spec(
            new Slo("order-service", "checkout-availability", 0.999, Duration.ofDays(30)),
            "sum(rate(http_server_requests_seconds_count{" + CHECKOUT + ", outcome=\"SERVER_ERROR\"}[{{window}}])) or vector(0)",
            "sum(rate(http_server_requests_seconds_count{" + CHECKOUT + "}[{{window}}]))",
            Duration.ofSeconds(30), 100, RUNBOOK);

    /**
     * 99% of successful checkout requests complete within 300 ms, over 30 days. Needs a histogram bucket at exactly
     * 0.3 s: {@code management.metrics.distribution.slo.http.server.requests=300ms}.
     */
    public static final Spec LATENCY = new Spec(
            new Slo("order-service", "checkout-latency", 0.99, Duration.ofDays(30)),
            "(sum(rate(http_server_requests_seconds_count{" + CHECKOUT + ", outcome=\"SUCCESS\"}[{{window}}]))"
                    + " - sum(rate(http_server_requests_seconds_bucket{" + CHECKOUT + ", outcome=\"SUCCESS\", le=\"0.3\"}[{{window}}])))",
            "sum(rate(http_server_requests_seconds_count{" + CHECKOUT + ", outcome=\"SUCCESS\"}[{{window}}]))",
            Duration.ofSeconds(30), 100, RUNBOOK);

    public static String rules() {
        return PrometheusSloRules.generate(AVAILABILITY, LATENCY);
    }

    public static void main(String[] args) {
        System.out.print(rules());
    }
}
