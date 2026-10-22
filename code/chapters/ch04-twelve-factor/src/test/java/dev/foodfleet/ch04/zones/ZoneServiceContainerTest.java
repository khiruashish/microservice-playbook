package dev.foodfleet.ch04.zones;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch04.zones.zone.ServiceabilityService;
import dev.foodfleet.ch04.zones.zone.Zone;
import dev.foodfleet.ch04.zones.zone.ZoneStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * X. Dev/prod parity in tests: real PostgreSQL 17 (the production major version), real Flyway
 * migrations, no H2. {@code @ServiceConnection} supplies the connection details, so the test has no
 * URLs or credentials. Skipped automatically when Docker isn't available.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "foodfleet.zones.city=Bengaluru",
                "foodfleet.zones.default-delivery-fee-paise=3500",
                "foodfleet.zones.max-delivery-km=7"
        })
@Testcontainers(disabledWithoutDocker = true)
class ZoneServiceContainerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private final ZoneStore zones;
    private final ServiceabilityService serviceability;

    ZoneServiceContainerTest(@Autowired ZoneStore zones, @Autowired ServiceabilityService serviceability) {
        this.zones = zones;
        this.serviceability = serviceability;
    }

    @Test
    void flywayAppliedSchemaAndSeedData() {
        assertThat(zones.find("560034")).contains(new Zone("560034", "Koramangala", true, 2_900L));
    }

    @Test
    void upsertIsIdempotent() {
        List<Zone> batch = List.of(new Zone("560001", "MG Road", true, 2_500L));
        zones.upsertAll(batch);
        long afterFirst = zones.count();
        zones.upsertAll(batch);

        assertThat(zones.count()).isEqualTo(afterFirst);
    }

    @Test
    void defaultFeeComesFromConfigurationNotTheDatabase() {
        assertThat(serviceability.check("560038").deliveryFeePaise()).isEqualTo(3_500L);
    }
}
