package dev.foodfleet.ch04.zones.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/** III. Config: bound from environment variables, validated, and fatal when wrong. */
class ZonePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void bindsFromEnvironmentVariablesUsingRelaxedBinding() {
        runner.withInitializer(context -> context.getEnvironment().getPropertySources().replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                Map.of("FOODFLEET_ZONES_CITY", "Bengaluru",
                                        "FOODFLEET_ZONES_DEFAULTDELIVERYFEEPAISE", "3500",
                                        "FOODFLEET_ZONES_MAXDELIVERYKM", "7"))))
                .run(context -> assertThat(context.getBean(ZoneProperties.class))
                        .isEqualTo(new ZoneProperties("Bengaluru", 3_500, 7)));
    }

    @Test
    void missingCityStopsTheServiceFromStarting() {
        runner.withPropertyValues(
                        "foodfleet.zones.default-delivery-fee-paise=3500",
                        "foodfleet.zones.max-delivery-km=7")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("city");
                });
    }

    @Test
    void absurdFeeStopsTheServiceFromStarting() {
        runner.withPropertyValues(
                        "foodfleet.zones.city=Bengaluru",
                        "foodfleet.zones.default-delivery-fee-paise=3500000", // ₹35,000: a typo, not a price
                        "foodfleet.zones.max-delivery-km=7")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("defaultDeliveryFeePaise");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ZoneProperties.class)
    static class PropertiesConfig {
    }
}
