package dev.foodfleet.ch45.chaos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToxicTest {

    @Test
    void latencyIsAddedToResponsesForEveryConnection() {
        assertThat(Toxic.latency(2000, 100).json()).isEqualTo(
                "{\"name\":\"latency\",\"type\":\"latency\",\"stream\":\"downstream\",\"toxicity\":1.0,\"attributes\":{\"latency\":2000,\"jitter\":100}}");
    }

    @Test
    void aBlackHoleSwallowsRequestsAndNeverCloses() {
        assertThat(Toxic.blackHole().json()).contains("\"type\":\"timeout\"", "\"timeout\":0", "\"stream\":\"upstream\"");
    }

    @Test
    void toxicityAndDirectionNarrowTheBlastRadius() {
        String json = Toxic.resetPeer(0).withToxicity(0.25).upstream().json();
        assertThat(json).contains("\"stream\":\"upstream\"", "\"toxicity\":0.25");
    }
}
