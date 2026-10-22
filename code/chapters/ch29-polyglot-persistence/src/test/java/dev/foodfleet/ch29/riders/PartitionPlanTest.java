package dev.foodfleet.ch29.riders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartitionPlanTest {

    final LocalDate today = LocalDate.of(2026, 10, 7);

    @Test
    void anEmptyTableGetsTheRetentionWindowAndTheDaysAhead() {
        PartitionPlan.Plan plan = PartitionPlan.plan(today, 7, 2, List.of());

        assertThat(plan.create()).extracting(PartitionPlan.Partition::name).containsExactly(
                "rider_pings_20261001", "rider_pings_20261002", "rider_pings_20261003", "rider_pings_20261004",
                "rider_pings_20261005", "rider_pings_20261006", "rider_pings_20261007", "rider_pings_20261008",
                "rider_pings_20261009");
        assertThat(plan.drop()).isEmpty();
    }

    @Test
    void daysPastRetentionAreDroppedOldestFirstAndOthersLeftAlone() {
        List<String> existing = List.of("rider_pings_20261007", "rider_pings_20260930", "rider_pings_20260929",
                "rider_pings_20261001", "rider_pings_default", "some_other_table");

        PartitionPlan.Plan plan = PartitionPlan.plan(today, 7, 1, existing);

        assertThat(plan.drop()).extracting(PartitionPlan.Partition::name)
                .containsExactly("rider_pings_20260929", "rider_pings_20260930");
        assertThat(plan.create()).extracting(PartitionPlan.Partition::name).doesNotContain("rider_pings_20261007")
                .contains("rider_pings_20261008");
    }

    @Test
    void theDdlCoversOneUtcDay() {
        assertThat(PartitionPlan.partitionFor(today).createSql()).isEqualTo(
                "CREATE TABLE IF NOT EXISTS rider_pings_20261007 PARTITION OF rider_pings"
                        + " FOR VALUES FROM ('2026-10-07 00:00:00+00') TO ('2026-10-08 00:00:00+00')");
    }

    @Test
    void retentionMustKeepToday() {
        assertThatThrownBy(() -> PartitionPlan.plan(today, 0, 1, List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
