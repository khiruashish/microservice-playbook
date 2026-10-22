package dev.foodfleet.ch49;

import dev.foodfleet.ch49.propagation.MessageHeaders;
import dev.foodfleet.ch49.propagation.TracedExecutors;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PropagationTest {

    static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
    static final String SPAN = "00f067aa0ba902b7";
    static final MessageHeaders HEADERS = new MessageHeaders(
            TextMapPropagator.composite(W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance()));

    static Context orderContext() {
        Span parent = Span.wrap(SpanContext.create(TRACE, SPAN, TraceFlags.getSampled(), TraceState.getDefault()));
        return Context.root().with(parent).with(Baggage.builder().put("tenant", "chain-7").build());
    }

    @Test
    void injectWritesTraceparentAndBaggage() {
        Map<String, String> h = HEADERS.inject(orderContext());

        assertThat(h.get("traceparent")).isEqualTo("00-" + TRACE + "-" + SPAN + "-01");
        assertThat(h.get("baggage")).isEqualTo("tenant=chain-7");
    }

    @Test
    void extractContinuesTheSameTraceAsARemoteParent() {
        Context c = HEADERS.extract(HEADERS.inject(orderContext()));
        SpanContext sc = Span.fromContext(c).getSpanContext();

        assertThat(sc.getTraceId()).isEqualTo(TRACE);
        assertThat(sc.getSpanId()).isEqualTo(SPAN);
        assertThat(sc.isRemote()).isTrue();
        assertThat(sc.isSampled()).isTrue();
        assertThat(Baggage.fromContext(c).getEntryValue("tenant")).isEqualTo("chain-7");
    }

    @Test
    void noHeadersMeansNoParent() {
        Context c = HEADERS.extract(Map.of("content-type", "application/json"));
        assertThat(Span.fromContext(c).getSpanContext().isValid()).isFalse();
    }

    @Test
    void anUnsampledParentStaysUnsampledDownstream() {
        Map<String, String> h = HEADERS.inject(Context.root().with(Span.wrap(
                SpanContext.create(TRACE, SPAN, TraceFlags.getDefault(), TraceState.getDefault()))));
        assertThat(h.get("traceparent")).isEqualTo("00-" + TRACE + "-" + SPAN + "-00");
    }

    static final ContextKey<String> ORDER = ContextKey.named("order");

    @Test
    void wrappedExecutorsCarryTheContextBareOnesDoNot() throws Exception {
        ExecutorService bare = Executors.newSingleThreadExecutor();
        ExecutorService wrapped = TracedExecutors.wrap(Executors.newSingleThreadExecutor());
        try (Scope ignored = Context.current().with(ORDER, "o-42").makeCurrent()) {
            assertThat(bare.submit(() -> Context.current().get(ORDER)).get()).isNull();
            assertThat(wrapped.submit(() -> Context.current().get(ORDER)).get()).isEqualTo("o-42");
        } finally {
            bare.shutdown();
            wrapped.shutdown();
        }
    }

    @Test
    void aHandRolledQueueNeedsTheWrappedTask() throws Exception {
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        AtomicReference<String> seen = new AtomicReference<>();
        try (Scope ignored = Context.current().with(ORDER, "o-7").makeCurrent()) {
            TracedExecutors.enqueue(queue, () -> seen.set(Context.current().get(ORDER)));
        }
        Thread worker = new Thread(() -> queue.poll().run());          // a worker with no context of its own
        worker.start();
        worker.join();
        assertThat(seen.get()).isEqualTo("o-7");
    }
}
