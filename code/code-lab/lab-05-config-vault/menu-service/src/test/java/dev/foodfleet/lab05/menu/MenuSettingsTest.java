package dev.foodfleet.lab05.menu;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/** Settings bind and validate at startup: a bad value stops the service before it takes traffic. */
class MenuSettingsTest {

    @Configuration
    @EnableConfigurationProperties(MenuSettings.class)
    static class Config {
    }

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    void bindsValidSettings() {
        runner.withPropertyValues("foodfleet.menu.discount-percent=10", "foodfleet.menu.max-items-per-order=50")
                .run(context -> assertThat(context.getBean(MenuSettings.class).discountPercent()).isEqualTo(10));
    }

    @Test
    void refusesToStartWithAnImpossibleDiscount() {
        runner.withPropertyValues("foodfleet.menu.discount-percent=500", "foodfleet.menu.max-items-per-order=50")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void theKeyNeverPrints() {
        assertThat(new PartnerApiKey("pk_live_123")).hasToString("PartnerApiKey[****]");
    }
}
