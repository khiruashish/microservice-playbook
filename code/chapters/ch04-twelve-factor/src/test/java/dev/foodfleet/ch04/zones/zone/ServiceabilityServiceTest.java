package dev.foodfleet.ch04.zones.zone;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch04.zones.config.ZoneProperties;
import dev.foodfleet.ch04.zones.zone.ServiceabilityService.Serviceability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class ServiceabilityServiceTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ServiceabilityService service = new ServiceabilityService(
            new InMemoryZoneStore().with(
                    new Zone("560034", "Koramangala", true, 2_900L),
                    new Zone("560038", "Indiranagar", true, null),
                    new Zone("562157", "Devanahalli", false, null)),
            new ZoneProperties("Bengaluru", 3_500, 7),
            registry);

    @Test
    void zoneFeeWinsOverDefault() {
        assertThat(service.check("560034"))
                .isEqualTo(new Serviceability("560034", "Koramangala", "Bengaluru", true, 2_900L));
    }

    @Test
    void configuredDefaultFeeAppliesWhenZoneHasNone() {
        assertThat(service.check("560038").deliveryFeePaise()).isEqualTo(3_500L);
    }

    @Test
    void notServiceableHasNoFee() {
        assertThat(service.check("562157"))
                .isEqualTo(new Serviceability("562157", "Devanahalli", "Bengaluru", false, null));
    }

    @Test
    void outcomesAreCountedForTheBusinessDashboard() {
        service.check("560034");
        service.check("562157");
        service.check("110001"); // Delhi: not in this city's zone table

        assertThat(count("serviceable")).isEqualTo(1);
        assertThat(count("not_serviceable")).isEqualTo(1);
        assertThat(count("unknown_pincode")).isEqualTo(1);
    }

    private double count(String result) {
        return registry.get("foodfleet.serviceability.checks").tag("result", result).counter().count();
    }
}
