package dev.foodfleet.ch26.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** The chapter's measurements, scaled down where they take long, as assertions about direction. */
class ExperimentsTest {

    @Test
    void handshakeAndFramingFollowRfc6455() throws Exception {
        // The example in RFC 6455, section 1.3
        assertThat(MiniWebSocketServer.accept("dGhlIHNhbXBsZSBub25jZQ==")).isEqualTo("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=");
        assertThat(HexFormat.of().formatHex(MiniWebSocketServer.textFrame("Hello"))).isEqualTo("810548656c6c6f");
        assertThat(MiniWebSocketServer.textFrame("x".repeat(200)).length).isEqualTo(204);   // 126 + 2-byte length
    }

    @Test
    void pushDeliversEveryUpdateForAFractionOfThePollingBytes() throws Exception {
        var polling = TransportComparison.run(TransportComparison.Transport.SHORT_POLLING_1S);
        var websocket = TransportComparison.run(TransportComparison.Transport.WEBSOCKET);

        assertThat(websocket.updatesSeen()).isEqualTo(40);
        assertThat(polling.updatesSeen()).isLessThan(15);                       // intermediate positions never seen
        assertThat(polling.bytesPerUpdateSeen()).isGreaterThan(5 * websocket.bytesPerUpdateSeen());
        assertThat(websocket.meanStalenessMs()).isLessThan(polling.meanStalenessMs() / 10);
    }

    @Test
    void aThreadPoolCapsStreamsAndVirtualThreadsDoNot() throws Exception {
        var pool = ConnectionCapacity.run("pool200", 500);
        var virtual = ConnectionCapacity.run("virtual", 500);

        assertThat(pool.streamsOpen()).isEqualTo(200);
        assertThat(virtual.streamsOpen()).isEqualTo(500);
        assertThat(virtual.received()).isEqualTo(500);
    }

    @Test
    void jitterSpreadsAReconnectStormAndSheddingStopsWastedWork() {
        var immediate = ReconnectStorm.run(ReconnectStorm.Policy.IMMEDIATE_THEN_1S, 26);
        var jitter = ReconnectStorm.run(ReconnectStorm.Policy.EXPONENTIAL_FULL_JITTER, 26);
        var shedding = ReconnectStorm.run(ReconnectStorm.Policy.IMMEDIATE_SERVER_SHEDS_STALE, 26);

        assertThat(immediate.wastedHandshakes()).isGreaterThan(100_000);
        assertThat(jitter.wastedHandshakes()).isEqualTo(0);
        assertThat(jitter.secondsTo99Percent()).isLessThan(immediate.secondsTo99Percent() / 1.8);
        assertThat(jitter.peakAttemptsPerSecond()).isLessThan(immediate.peakAttemptsPerSecond() / 5);
        assertThat(shedding.wastedHandshakes()).isEqualTo(0);
        assertThat(shedding.secondsTo99Percent()).isLessThan(immediate.secondsTo99Percent());
    }
}
