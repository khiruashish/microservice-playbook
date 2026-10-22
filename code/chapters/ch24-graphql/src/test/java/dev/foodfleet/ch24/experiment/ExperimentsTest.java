package dev.foodfleet.ch24.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The chapter's tables, as assertions about what they show. */
class ExperimentsTest {

    @Test
    void graphqlCutsRoundTripsAndBytesButOnlyBatchingCutsBackendCalls() {
        List<ScreenComparison.Row> rows = ScreenComparison.run();
        ScreenComparison.Row rest = rows.get(0);
        ScreenComparison.Row perItem = rows.get(1);
        ScreenComparison.Row batched = rows.get(2);
        ScreenComparison.Row bff = rows.get(3);

        assertThat(rest.phoneRequests()).isEqualTo(12);
        assertThat(perItem.phoneRequests()).isEqualTo(1);
        assertThat(perItem.responseGzipBytes()).isLessThan(rest.responseGzipBytes() / 5);
        assertThat(perItem.backendCalls()).isEqualTo(19);
        assertThat(batched.backendCalls()).isEqualTo(3);
        assertThat(perItem.latencyMs()).isGreaterThan(rest.latencyMs());       // N+1 can be slower than REST
        assertThat(batched.latencyMs()).isLessThan(rest.latencyMs());
        assertThat(Math.abs(batched.responseBytes() - bff.responseBytes())).isLessThan(50);   // same data on the wire
    }

    @Test
    void eachNestingLevelMultipliesTheWorkByThePageSize() {
        QueryCost.Row three = QueryCost.measure(3, 10);
        QueryCost.Row four = QueryCost.measure(4, 10);

        assertThat(three.complexity()).isEqualTo(1443L);
        assertThat((long) three.resolvedFields()).isEqualTo(three.complexity());
        assertThat(four.complexity()).isGreaterThan(9 * three.complexity());
        assertThat(three.allowed()).isTrue();
        assertThat(four.allowed()).isFalse();
        assertThat(four.callsBatched()).isEqualTo(8);                            // batching keeps calls linear...
        assertThat(four.resolvedFields()).isGreaterThan(14_000);                // ...but not the work
    }
}
