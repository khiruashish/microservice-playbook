package dev.foodfleet.ch27.compat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The chapter's change-history table, as assertions about what it shows. */
class CompatibilityTest {

    final List<ChangeHistory.Verdict> asBuilt = ChangeHistory.run(false);
    final List<ChangeHistory.Verdict> tolerant = ChangeHistory.run(true);

    @Test
    void strictReadersTurnAdditiveChangesIntoBreakingOnes() {
        ChangeHistory.Verdict addField = asBuilt.get(0);

        assertThat(addField.brokenConsumers()).isEqualTo(List.of("mobile-app"));
        assertThat(addField.diff()).isEqualTo(StructuralDiff.Severity.NONE);   // tools call it safe
        assertThat(tolerant.get(0).breaksSomeone()).isFalse();
        assertThat(asBuilt.stream().filter(ChangeHistory.Verdict::breaksSomeone).count()).isEqualTo(11L);
        assertThat(tolerant.stream().filter(ChangeHistory.Verdict::breaksSomeone).count()).isEqualTo(8L);
    }

    @Test
    void diffAndContractsCatchDifferentThings() {
        ChangeHistory.Verdict removeUnused = asBuilt.get(5);
        ChangeHistory.Verdict behaviour = asBuilt.get(10);
        ChangeHistory.Verdict meaning = asBuilt.get(7);

        assertThat(removeUnused.caughtByDiff()).isTrue();                         // a false alarm...
        assertThat(removeUnused.breaksSomeone()).isFalse();
        assertThat(removeUnused.caughtByContracts()).isFalse();                   // ...contracts know nobody uses it
        assertThat(behaviour.caughtByDiff()).isFalse();                           // 404 -> 200 isn't in the schema
        assertThat(behaviour.caughtByContracts()).isTrue();
        assertThat(meaning.breaksSomeone()).isTrue();                             // nothing catches a change of meaning
        assertThat(meaning.caughtByDiff() || meaning.caughtByContracts()).isFalse();
    }

    @Test
    void theDiffFlagsRemovalsTypeChangesAndOptionality() {
        Schema v1 = ChangeHistory.v1();
        Schema v2 = v1.without("legacyCode").change("riderName", Schema.Field::optional)
                .change("totalPaise", f -> f.withType(Schema.Type.STRING)).with("tipPaise", Schema.Field.integer("").optional());

        List<StructuralDiff.Finding> findings = StructuralDiff.compare(v1, v2);

        assertThat(findings.size()).isEqualTo(3);                                 // the added field isn't one
        assertThat(StructuralDiff.worst(findings)).isEqualTo(StructuralDiff.Severity.ERROR);
    }
}
