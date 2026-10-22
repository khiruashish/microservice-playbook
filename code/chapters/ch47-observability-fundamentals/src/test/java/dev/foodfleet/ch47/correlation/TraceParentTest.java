package dev.foodfleet.ch47.correlation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceParentTest {

    /** The example from the W3C Trace Context specification. */
    private static final String EXAMPLE = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Test
    void parsesTheSpecificationsExample() {
        TraceParent t = TraceParent.parse(EXAMPLE).orElseThrow();

        assertThat(t.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(t.spanId()).isEqualTo("00f067aa0ba902b7");
        assertThat(t.sampled()).isTrue();
        assertThat(t.header()).isEqualTo(EXAMPLE);
    }

    @Test
    void theSampledFlagIsOneBitAmongOthers() {
        assertThat(TraceParent.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-03").orElseThrow().sampled()).isTrue();
        assertThat(TraceParent.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-02").orElseThrow().sampled()).isFalse();
    }

    @Test
    void invalidHeadersAreIgnoredNotFatal() {
        assertThat(TraceParent.parse(null)).isEmpty();
        assertThat(TraceParent.parse("garbage")).isEmpty();
        assertThat(TraceParent.parse("00-00000000000000000000000000000000-00f067aa0ba902b7-01")).isEmpty();   // zero trace ID
        assertThat(TraceParent.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01")).isEmpty();   // zero span ID
        assertThat(TraceParent.parse("00-4BF92F3577B34DA6A3CE929D0E0E4736-00f067aa0ba902b7-01")).isEmpty();   // upper case
        assertThat(TraceParent.parse("ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")).isEmpty();   // version ff
        assertThat(TraceParent.parse(EXAMPLE + "-extra")).isEmpty();                                           // 00 is exact
    }

    @Test
    void laterVersionsMayCarryMoreFields() {
        assertThat(TraceParent.parse("01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01-what-the-future-holds"))
                .isPresent();
    }

    @Test
    void aChildKeepsTheTraceAndTheDecision() {
        TraceParent parent = TraceParent.parse(EXAMPLE).orElseThrow();
        TraceParent child = parent.child();

        assertThat(child.traceId()).isEqualTo(parent.traceId());
        assertThat(child.spanId()).isNotEqualTo(parent.spanId());
        assertThat(child.sampled()).isTrue();
    }

    @Test
    void newRootsHaveValidIds() {
        for (int i = 0; i < 1000; i++) {
            TraceParent t = TraceParent.newRoot(false);
            assertThat(TraceParent.parse(t.header())).isPresent();
        }
        assertThatThrownBy(() -> new TraceParent("abc", "00f067aa0ba902b7", true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("trace-id");
    }
}
