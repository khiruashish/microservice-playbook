package dev.foodfleet.ch11.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Two healthy instances registered under one name: requests alternate between them. */
@SpringBootTest(properties = {"spring.main.web-application-type=none", "foodfleet.demo.run=false"})
class RoundRobinDiscoveryTest {

    static final KitchenStubs.Stub A = KitchenStubs.start("kitchen-a");
    static final KitchenStubs.Stub B = KitchenStubs.start("kitchen-b");

    @DynamicPropertySource
    static void registry(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.kitchen-service[0].uri", A::uri);
        registry.add("spring.cloud.discovery.client.simple.instances.kitchen-service[1].uri", B::uri);
    }

    @Autowired
    KitchenClient kitchen;

    @Test
    void callsByNameAreSpreadRoundRobin() {
        Set<String> answeredBy = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            answeredBy.add(kitchen.capacity().instance());
        }

        assertThat(answeredBy).containsExactlyInAnyOrder("kitchen-a", "kitchen-b");
        assertThat(A.hits().get()).isEqualTo(3);
        assertThat(B.hits().get()).isEqualTo(3);
    }

    @AfterAll
    static void stop() {
        A.server().stop(0);
        B.server().stop(0);
    }
}
