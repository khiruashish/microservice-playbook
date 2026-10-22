package dev.foodfleet.ch11.kitchen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.ResourceAccessException;

/**
 * The registry still lists an instance that has crashed (as every registry does for a while).
 * Without a retry, every second call fails; with one retry through the load balancer, none do.
 */
@SpringBootTest(properties = {"spring.main.web-application-type=none", "foodfleet.demo.run=false"})
class StaleRegistryTest {

    static final KitchenStubs.Stub ALIVE = KitchenStubs.start("kitchen-a");
    static final String DEAD = KitchenStubs.deadInstanceUri();

    @DynamicPropertySource
    static void registry(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.kitchen-service[0].uri", ALIVE::uri);
        registry.add("spring.cloud.discovery.client.simple.instances.kitchen-service[1].uri", () -> DEAD);
    }

    @Autowired
    KitchenClient kitchen;

    @Autowired
    KitchenCapacityLookup lookup;

    @Test
    void withoutRetryHalfTheCallsFail() {
        int failures = 0;
        for (int i = 0; i < 10; i++) {
            try {
                kitchen.capacity();
            } catch (ResourceAccessException connectionRefused) {
                failures++;
            }
        }
        assertThat(failures).isEqualTo(5);
    }

    @Test
    void oneRetryThroughTheLoadBalancerHidesTheDeadInstance() {
        for (int i = 0; i < 10; i++) {
            assertThat(lookup.capacity().instance()).isEqualTo("kitchen-a");
        }
    }

    @AfterAll
    static void stop() {
        ALIVE.server().stop(0);
    }
}
