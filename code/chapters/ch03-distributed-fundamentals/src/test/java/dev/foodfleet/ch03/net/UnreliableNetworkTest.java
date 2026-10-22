package dev.foodfleet.ch03.net;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch03.net.UnreliableNetwork.Envelope;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Fallacy #1 ("the network is reliable"), made executable. */
class UnreliableNetworkTest {

    @Test
    void theNetworkWeImagine_everyMessageOnceInOrder() {
        UnreliableNetwork<Integer> network = UnreliableNetwork.reliable();
        IntStream.range(0, 100).forEach(i -> network.send("m-" + i, i));

        List<Envelope<Integer>> delivered = network.drain();

        assertThat(delivered).extracting(Envelope::payload)
                .containsExactlyElementsOf(IntStream.range(0, 100).boxed().toList());
    }

    @Test
    void theNetworkWeGet_dropsDuplicatesAndReorders() {
        UnreliableNetwork<Integer> network = UnreliableNetwork.of(42, 0.05, 0.10, 5);
        IntStream.range(0, 1_000).forEach(i -> network.send("m-" + i, i));

        List<Envelope<Integer>> delivered = network.drain();

        assertThat(network.dropped()).as("lost").isPositive();
        assertThat(network.duplicated()).as("duplicated").isPositive();
        assertThat(delivered).hasSize((int) (network.sent() - network.dropped() + network.duplicated()));
        assertThat(isSorted(delivered)).as("arrived in send order").isFalse();
    }

    private static boolean isSorted(List<Envelope<Integer>> delivered) {
        for (int i = 1; i < delivered.size(); i++) {
            if (delivered.get(i).payload() < delivered.get(i - 1).payload()) {
                return false;
            }
        }
        return true;
    }
}
