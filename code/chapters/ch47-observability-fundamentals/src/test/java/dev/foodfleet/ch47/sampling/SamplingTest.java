package dev.foodfleet.ch47.sampling;

import dev.foodfleet.ch47.correlation.TraceParent;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SamplingTest {

    @Test
    void zeroKeepsNothingAndOneKeepsEverything() {
        TraceIdRatio none = new TraceIdRatio(0.0);
        TraceIdRatio all = new TraceIdRatio(1.0);
        for (int i = 0; i < 10_000; i++) {
            String id = TraceParent.newRoot(true).traceId();
            assertThat(none.sampled(id)).isFalse();
            assertThat(all.sampled(id)).isTrue();
        }
    }

    @Test
    void keepsTheConfiguredShareOfTraces() {
        TraceIdRatio tenPercent = new TraceIdRatio(0.1);
        int kept = 0;
        for (int i = 0; i < 100_000; i++) {
            if (tenPercent.sampled(TraceParent.newRoot(true).traceId())) {
                kept++;
            }
        }
        assertThat((long) kept).isBetween(9_500, 10_500);
    }

    @Test
    void everyServiceDecidesTheSameFromTheTraceIdAlone() {
        TraceIdRatio a = new TraceIdRatio(0.05);
        TraceIdRatio b = new TraceIdRatio(0.05);
        for (int i = 0; i < 10_000; i++) {
            String id = TraceParent.newRoot(true).traceId();
            assertThat(a.sampled(id) == b.sampled(id)).isTrue();
        }
    }

    @Test
    void onlyTheLower64BitsCount() {
        TraceIdRatio r = new TraceIdRatio(0.5);
        assertThat(r.sampled("00000000000000000000000000000001")).isTrue();
        assertThat(r.sampled("ffffffffffffffff0000000000000001")).isTrue();
        assertThat(r.sampled("000000000000000070000000000000ff")).isFalse();    // 0x70.. is above half of Long.MAX_VALUE
        // like the SDK: |Long.MIN_VALUE| is still negative, so this one ID is kept at any ratio above zero
        assertThat(new TraceIdRatio(0.000001).sampled("00000000000000008000000000000000")).isTrue();
        assertThatThrownBy(() -> new TraceIdRatio(1.5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tailSamplingKeepsErrorsAndSlowTracesAndABaseline() {
        TailPolicy policy = TailPolicy.foodFleet();
        String id = "4bf92f3577b34da6a3ce929d0e0e4736";

        assertThat(policy.decide(id, true, Duration.ofMillis(5))).isEqualTo(TailPolicy.Decision.KEEP_ERROR);
        assertThat(policy.decide(id, false, Duration.ofMillis(301))).isEqualTo(TailPolicy.Decision.KEEP_SLOW);
        // the collector's latency policy keeps traces strictly longer than the threshold
        assertThat(policy.decide(id, false, Duration.ofMillis(300))).isNotEqualTo(TailPolicy.Decision.KEEP_SLOW);
        int baseline = 0;
        for (int i = 0; i < 100_000; i++) {
            if (policy.decide(TraceParent.newRoot(true).traceId(), false, Duration.ofMillis(20)) == TailPolicy.Decision.KEEP_BASELINE) {
                baseline++;
            }
        }
        assertThat((long) baseline).isBetween(800, 1_200);
    }
}
