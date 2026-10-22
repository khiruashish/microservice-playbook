package dev.foodfleet.ch24.graph;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch24.experiment.FoodFleetBackends;
import dev.foodfleet.ch24.experiment.QueryCost;
import dev.foodfleet.ch24.experiment.ScreenComparison;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester;
import org.springframework.graphql.test.tester.GraphQlTester;

/**
 * The real schema, controller, batch loading, limits and error mapping, executed by graphql-java
 * through Spring for GraphQL (no HTTP). The customer id is put in the context as the header
 * interceptor would.
 */
@GraphQlTest(CustomerGraphController.class)
@Import(GraphQlConfig.class)
class CustomerGraphControllerTest {

    @Autowired
    ExecutionGraphQlService service;

    @Autowired
    FoodFleetBackends services;

    GraphQlTester tester;

    @BeforeEach
    void signedInCustomer() {
        tester = ExecutionGraphQlServiceTester.builder(service)
                .configureExecutionInput((input, builder) ->
                        builder.graphQLContext(Map.of("customerId", "c-1001")).build())
                .build();
        services.reset();
    }

    @Test
    void theOrdersScreenNeedsThreeServiceCalls() {
        GraphQlTester.Response response = tester.document(ScreenComparison.SCREEN_QUERY).execute();

        response.path("myOrders[*].id").entityList(String.class).hasSize(10);
        response.path("myOrders[0].restaurant.name").entity(String.class).isEqualTo("Meghana Foods");
        response.path("myOrders[3].rider").valueIsNull();                       // cancelled, no rider
        assertThat(services.totalCalls()).isEqualTo(3);                          // orders + 1 restaurant batch + 1 rider batch
    }

    @Test
    void anUnreasonablePageSizeIsABadRequest() {
        tester.document("{ myOrders(first: 500) { id } }").execute()
                .errors().expect(e -> e.getErrorType() == ErrorType.BAD_REQUEST).verify();
    }

    @Test
    void anExpensiveQueryIsRejectedBeforeAnyServiceIsCalled() {
        tester.document(QueryCost.nested(3, 10)).execute().errors().verify();   // complexity 1,443: allowed
        services.reset();

        tester.document(QueryCost.nested(4, 10)).execute().errors()
                .expect(e -> e.getMessage().startsWith("maximum query")).verify();
        assertThat(services.totalCalls()).isEqualTo(0);
    }
}
