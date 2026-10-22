package dev.foodfleet.ch49.spans;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.util.function.Supplier;

/**
 * A manual span around the part of payment-service that instrumentation can't see: the fraud decision. Attribute
 * names follow OpenTelemetry's conventions (lower-case, dot-separated, namespaced); the outcome is an attribute and,
 * on failure, the span's status and an exception event. Baggage that is worth querying is copied onto the span here,
 * because nothing copies it automatically.
 */
public final class PaymentSpans {

    public static final AttributeKey<String> PAYMENT_METHOD = AttributeKey.stringKey("foodfleet.payment.method");
    public static final AttributeKey<String> DECISION = AttributeKey.stringKey("foodfleet.fraud.decision");
    public static final AttributeKey<String> TENANT = AttributeKey.stringKey("foodfleet.tenant");
    public static final AttributeKey<Long> RULES = AttributeKey.longKey("foodfleet.fraud.rules_evaluated");

    private final Tracer tracer;

    public PaymentSpans(Tracer tracer) {
        this.tracer = tracer;
    }

    /** The result of a fraud check. */
    public record Decision(String outcome, long rulesEvaluated) {
    }

    public Decision fraudCheck(String paymentMethod, Supplier<Decision> check) {
        Span span = tracer.spanBuilder("fraud check")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(PAYMENT_METHOD, paymentMethod)
                .startSpan();
        String tenant = Baggage.current().getEntryValue("tenant");
        if (tenant != null) {
            span.setAttribute(TENANT, tenant);
        }
        try (Scope ignored = span.makeCurrent()) {
            Decision d = check.get();
            span.setAttribute(DECISION, d.outcome());
            span.setAttribute(RULES, d.rulesEvaluated());
            if (!"approve".equals(d.outcome())) {
                span.addEvent("payment held for review", Attributes.of(DECISION, d.outcome()));
            }
            return d;
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
}
