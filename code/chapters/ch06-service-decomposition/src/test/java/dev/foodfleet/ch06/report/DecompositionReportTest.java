package dev.foodfleet.ch06.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DecompositionReportTest {

    @Test
    void rendersOneRowPerCandidateAndTheSuggestedGroups() {
        String report = new DecompositionReport().render(List.of(
                "c1|order,order-line", "c2|order,order-line", "c3|order,order-line", "c4|coupon"));

        assertThat(report)
                .contains("A. One service per entity")
                .contains("B. By business capability")
                .contains("C. Three coarse services")
                .contains("D. Modular monolith (one deployable)")
                .contains(" - order, order-line")
                .contains(" - coupon");
    }
}
