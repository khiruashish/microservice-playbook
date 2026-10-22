package dev.foodfleet.ch24.graph;

import graphql.GraphqlErrorBuilder;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.server.support.HttpRequestHeaderInterceptor;

/**
 * Protection and conventions around the graph: query limits checked before execution, errors
 * mapped to GraphQL error types, the customer id copied from the gateway's header, and a startup
 * report of schema fields with no resolver.
 */
@Configuration(proxyBeanMethods = false)
public class GraphQlConfig {

    private static final Logger log = LoggerFactory.getLogger(GraphQlConfig.class);

    public static final int MAX_DEPTH = 8;
    public static final int MAX_COMPLEXITY = 2_000;

    @Bean
    Instrumentation maxDepth() {
        return new MaxQueryDepthInstrumentation(MAX_DEPTH);
    }

    /**
     * graphql-java counts each field once by default. This calculator multiplies a list field's
     * children by its {@code first} argument, so the score tracks the work the query will cause.
     */
    @Bean
    Instrumentation maxComplexity() {
        return new MaxQueryComplexityInstrumentation(MAX_COMPLEXITY, (env, childComplexity) -> {
            int first = env.getArguments().get("first") instanceof Integer n ? n : 1;
            return 1 + first * childComplexity;
        });
    }

    @Bean
    DataFetcherExceptionResolver badArguments() {
        return DataFetcherExceptionResolver.forSingleError((ex, env) -> ex instanceof IllegalArgumentException
                ? GraphqlErrorBuilder.newError(env).errorType(ErrorType.BAD_REQUEST).message(ex.getMessage()).build()
                : null);
    }

    /** The gateway verifies the token and sets X-Customer-Id (chapter 10); resolvers read it as @ContextValue. */
    @Bean
    HttpRequestHeaderInterceptor customerHeader() {
        return HttpRequestHeaderInterceptor.builder().mapHeaderToKey("X-Customer-Id", "customerId").build();
    }

    @Bean
    GraphQlSourceBuilderCustomizer schemaInspection() {
        return builder -> builder.inspectSchemaMappings(report -> log.info("{}", report));
    }
}
