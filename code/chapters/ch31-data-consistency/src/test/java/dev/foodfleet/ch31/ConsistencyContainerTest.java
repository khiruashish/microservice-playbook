package dev.foodfleet.ch31;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch31.points.InsufficientPoints;
import dev.foodfleet.ch31.points.LoyaltyPoints;
import dev.foodfleet.ch31.profiles.Profile;
import dev.foodfleet.ch31.profiles.ProfileService;
import dev.foodfleet.ch31.profiles.VersionConflict;
import dev.foodfleet.ch31.reads.ReplicaRouter;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL in a container plays both roles: as "replica" it reports no replay position, so the router
 * must not trust it and reads from the primary. Replication lag itself is exercised in verify/ against real
 * streaming replicas. Skipped without Docker.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ConsistencyContainerTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databases(DynamicPropertyRegistry registry) {
        for (String role : new String[] {"primary", "replica"}) {
            registry.add("app.datasource." + role + ".url", postgres::getJdbcUrl);
            registry.add("app.datasource." + role + ".username", postgres::getUsername);
            registry.add("app.datasource." + role + ".password", postgres::getPassword);
        }
    }

    @Autowired ProfileService profiles;
    @Autowired LoyaltyPoints points;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void seed() {
        jdbc.sql("TRUNCATE profiles, loyalty").update();
        jdbc.sql("INSERT INTO profiles (customer_id, address) VALUES (7, '12 MG Road')").update();
    }

    @Test
    void anUpdateAgainstTheCurrentVersionSucceedsAndReturnsAToken() {
        ProfileService.Written w = profiles.update(7, "4 Brigade Road", 1);

        assertThat(w.profile().version()).isEqualTo(2);
        ReplicaRouter.Routed<Optional<Profile>> read = profiles.find(7, Optional.of(w.token()));
        assertThat(read.value().orElseThrow().version()).isEqualTo(2);
        assertThat(read.source()).isEqualTo(ReplicaRouter.Source.PRIMARY);     // the "replica" isn't replaying
        assertThat(read.token().isAtLeast(w.token())).isTrue();
    }

    @Test
    void anUpdateBasedOnAnOldVersionIsRefusedInsteadOfOverwritingTheNewer() {
        profiles.update(7, "first edit", 1);

        assertThatThrownBy(() -> profiles.update(7, "edit made on a stale screen", 1))
                .isInstanceOf(VersionConflict.class).hasMessageContaining("version 2");
        assertThat(profiles.find(7, Optional.empty()).value().orElseThrow().address()).isEqualTo("first edit");
    }

    @Test
    void concurrentPointsAreNeverLost() throws Exception {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 800; i++) {
                pool.submit(() -> points.add(42, 10));
            }
        }
        assertThat(points.redeem(42, 1)).isEqualTo(7_999);
        assertThatThrownBy(() -> points.redeem(42, 10_000)).isInstanceOf(InsufficientPoints.class);
    }
}
