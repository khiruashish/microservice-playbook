package dev.foodfleet.ch06.decomposition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch06.decomposition.CoChangeAnalyzer.Coupling;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CoChangeAnalyzerTest {

    private final CoChangeAnalyzer analyzer = new CoChangeAnalyzer();

    @Test
    void computesCouplingDegreeAgainstTheLessChangedModule() {
        List<Set<String>> commits = CoChangeAnalyzer.parseCommitLog(List.of(
                "c1|order,order-line", "c2|order,order-line", "c3|order", "c4|order,coupon"));

        List<Coupling> couplings = analyzer.couplings(commits, 1);

        assertThat(couplings.getFirst()).isEqualTo(new Coupling("order", "order-line", 2, 4, 2, 1.0));
        assertThat(couplings).contains(new Coupling("coupon", "order", 1, 1, 4, 1.0));
    }

    @Test
    void foodFleetHistoryRediscoversTheCapabilityBoundaries() throws IOException {
        List<Set<String>> commits = CoChangeAnalyzer.parseCommitLog(commitLog());

        List<Set<String>> groups = analyzer.suggestGroups(commits, 0.6, 3);

        assertThat(commits).hasSize(40);
        assertThat(groups).contains(
                Set.of("cart", "order", "order-line"),
                Set.of("address-book", "customer-profile"),
                Set.of("menu", "menu-item-pricing"),
                Set.of("rider-assignment", "zone"),
                Set.of("coupon"),
                Set.of("payment"));
        assertThat(groups.stream().mapToInt(Set::size).sum()).as("every module in exactly one group").isEqualTo(14);
    }

    @Test
    void malformedLinesAreRejected() {
        assertThatThrownBy(() -> CoChangeAnalyzer.parseCommitLog(List.of("c1 order,cart")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<String> commitLog() throws IOException {
        try (InputStream in = CoChangeAnalyzerTest.class.getResourceAsStream("/foodfleet-commits.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }
}
