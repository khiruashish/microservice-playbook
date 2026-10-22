package dev.foodfleet.ch47.analysis;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class BubbleUpTest {

    private static final Predicate<Map<String, String>> SLOW = e -> Double.parseDouble(e.get("duration_ms")) > 300;
    private final BubbleUp bubbleUp = new BubbleUp(Set.of("duration_ms", "trace_id"));

    /** Payments where 1.5 + UPI + bank-x is slow; chain-1 is the biggest tenant and a red herring. */
    private static List<Map<String, String>> payments() {
        Random r = new Random(47);
        List<Map<String, String>> out = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            String version = r.nextBoolean() ? "1.5" : "1.4";
            String method = r.nextDouble() < 0.5 ? "UPI" : "CARD";
            String bank = "bank-" + "abcdx".charAt(r.nextInt(5));
            String tenant = r.nextDouble() < 0.4 ? "chain-1" : "chain-" + (2 + r.nextInt(30));
            boolean slow = version.equals("1.5") && method.equals("UPI") && bank.equals("bank-x");
            out.add(Map.of("service.version", version, "payment.method", method, "payment.bank", bank, "tenant", tenant,
                    "trace_id", Integer.toHexString(i), "duration_ms", slow ? "612" : String.valueOf(2 + r.nextInt(7))));
        }
        return out;
    }

    @Test
    void ranksTheValuesThatAreOverRepresentedAmongSlowRequests() {
        List<BubbleUp.Difference> ranked = bubbleUp.rank(payments(), SLOW);

        assertThat(ranked.subList(0, 3)).extracting(BubbleUp.Difference::field)
                .contains("payment.bank", "service.version", "payment.method");
        assertThat(ranked.get(0).inSelection()).isEqualTo(1.0);
        // the big tenant is common everywhere, so it isn't a difference
        BubbleUp.Difference chain1 = ranked.stream().filter(d -> d.value().equals("chain-1")).findFirst().orElseThrow();
        assertThat(Math.abs(chain1.lift())).isLessThan(0.05);
    }

    @Test
    void combinesTheTopValuesIntoAFilterThatIsolatesTheProblem() {
        BubbleUp.Isolation iso = bubbleUp.isolate(payments(), SLOW, 4, 0.9);

        assertThat(iso.filter()).isEqualTo(Map.of("payment.bank", "bank-x", "service.version", "1.5", "payment.method", "UPI"));
        assertThat(iso.precision()).isEqualTo(1.0);
        assertThat(iso.recall()).isEqualTo(1.0);
    }

    @Test
    void ignoredFieldsAreNeverCandidates() {
        assertThat(bubbleUp.rank(payments(), SLOW)).filteredOn(d -> d.field().equals("trace_id")).isEmpty();
    }

    @Test
    void withNothingSelectedThereIsNothingToIsolate() {
        BubbleUp.Isolation iso = bubbleUp.isolate(payments(), e -> false, 4, 0.9);

        assertThat(iso.filter()).isEmpty();
        assertThat(iso.recall()).isEqualTo(0.0);
    }
}
