package dev.foodfleet.ch24.mini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch24.experiment.FoodFleetBackends;
import dev.foodfleet.ch24.experiment.FoodFleetGraph;
import dev.foodfleet.ch24.experiment.ScreenComparison;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EngineTest {

    @Test
    void parsesFieldsArgumentsAndNesting() {
        Query q = Query.parse("query Screen { myOrders(first: 2) { id, restaurant { name } } # comment\n }");

        assertThat(q.fields().size()).isEqualTo(1);
        assertThat(q.fields().getFirst().arguments()).isEqualTo(Map.of("first", 2));
        assertThat(q.depth()).isEqualTo(3);
        assertThatThrownBy(() -> Query.parse("{ myOrders(first: 2) { id }")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnsOnlyTheSelectedFields() {
        FoodFleetBackends b = new FoodFleetBackends();
        Map<String, Object> result = FoodFleetGraph.engine(b)
                .execute(Query.parse("{ myOrders(first: 1) { id restaurant { name } } }"), Engine.Mode.BATCHED, "root");

        assertThat(Json.write(result)).isEqualTo(
                "{\"data\":{\"myOrders\":[{\"id\":\"o-9001\",\"restaurant\":{\"name\":\"Meghana Foods\"}}]}}");
    }

    @Test
    void perItemResolversCallOncePerParentAndBatchingOncePerLevel() {
        FoodFleetBackends b = new FoodFleetBackends();
        Engine engine = FoodFleetGraph.engine(b);
        Query screen = Query.parse(ScreenComparison.SCREEN_QUERY);

        engine.execute(screen, Engine.Mode.PER_ITEM, "root");
        assertThat(b.totalCalls()).isEqualTo(1 + 10 + 8);                      // orders, restaurant per order, rider per assigned order
        b.reset();
        engine.execute(screen, Engine.Mode.BATCHED, "root");
        assertThat(b.totalCalls()).isEqualTo(3);
        assertThat(b.calls().get("restaurant-service: batch")).isEqualTo(1);
    }

    @Test
    void batchingChangesTheCallsNotTheAnswer() {
        assertThat(ScreenComparison.graphqlResponse(Engine.Mode.PER_ITEM))
                .isEqualTo(ScreenComparison.graphqlResponse(Engine.Mode.BATCHED));
        assertThat(ScreenComparison.graphqlResponse(Engine.Mode.BATCHED)).contains("\"rider\":null");   // cancelled orders
    }

    @Test
    void staticComplexityPredictsTheWorkBeforeExecuting() {
        FoodFleetBackends b = new FoodFleetBackends();
        Engine engine = FoodFleetGraph.engine(b);
        Query q = Query.parse("{ restaurant(id: \"r-11\") { name reviews(first: 5) { rating restaurant { name } } } }");

        long predicted = engine.complexity(q);
        assertThat(b.totalCalls()).isEqualTo(0);                                 // nothing ran yet
        engine.execute(q, Engine.Mode.BATCHED, "root");

        assertThat((long) engine.resolvedFields()).isEqualTo(predicted);
        assertThat(predicted).isEqualTo(18L);                                   // 3 + 5 x (rating, restaurant, name)
    }
}
