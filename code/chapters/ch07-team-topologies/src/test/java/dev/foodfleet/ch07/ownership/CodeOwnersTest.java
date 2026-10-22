package dev.foodfleet.ch07.ownership;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch07.ownership.CodeOwners.Finding;
import dev.foodfleet.ch07.topology.FoodFleetTeams;
import dev.foodfleet.ch07.topology.Topology;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodeOwnersTest {

    private final CodeOwners codeOwners = new CodeOwners("foodfleet");
    private final Topology topology = FoodFleetTeams.after();

    @Test
    void generatedFileRoundTripsWithoutFindings() {
        String generated = codeOwners.generate(topology);

        assertThat(generated).contains("/services/ordering/                @foodfleet/checkout");
        assertThat(codeOwners.check(topology, CodeOwners.parse(generated.lines().toList()))).isEmpty();
    }

    @Test
    void lastMatchingLineWinsAsOnGitHub() {
        CodeOwners.Parsed parsed = CodeOwners.parse(List.of(
                "*                     @foodfleet/platform",
                "/services/            @foodfleet/checkout",
                "/services/payment/    @foodfleet/payments"));

        assertThat(CodeOwners.effectiveRule(parsed.rules(), "services/payment/").orElseThrow().owners())
                .containsExactly("@foodfleet/payments");
        assertThat(CodeOwners.effectiveRule(parsed.rules(), "services/zone/").orElseThrow().owners())
                .containsExactly("@foodfleet/checkout");
    }

    @Test
    void reportsDriftSharedOwnershipAndGaps() {
        CodeOwners.Parsed parsed = CodeOwners.parse(List.of(
                "/services/ordering/   @foodfleet/checkout",
                "/services/zone/       @foodfleet/checkout",
                "/services/payment/    @foodfleet/payments @foodfleet/checkout",
                "*.md                  @foodfleet/docs"));

        List<Finding> findings = codeOwners.check(topology, parsed);

        assertThat(parsed.skipped()).containsExactly("line 4: *.md");
        assertThat(findings).anySatisfy(f -> {
            assertThat(f.component()).isEqualTo("zone");
            assertThat(f.problem()).isEqualTo("owned by @foodfleet/checkout (line 2), topology says @foodfleet/logistics");
        });
        assertThat(findings).anySatisfy(f -> {
            assertThat(f.component()).isEqualTo("payment");
            assertThat(f.problem()).startsWith("shared by @foodfleet/payments @foodfleet/checkout");
        });
        assertThat(findings).filteredOn(f -> f.problem().equals("no owner")).hasSize(topology.components().size() - 3);
    }
}
