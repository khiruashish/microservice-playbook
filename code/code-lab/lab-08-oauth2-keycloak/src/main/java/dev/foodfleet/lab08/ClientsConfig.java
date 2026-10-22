package dev.foodfleet.lab08;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
@ImportHttpServices(group = "menu", types = MenuClient.class)
public class ClientsConfig {

    /** Adds the token relay to the menu group's RestClient only: not every outbound call should carry a user's token. */
    @Bean
    RestClientHttpServiceGroupConfigurer tokenRelayForMenu() {
        return groups -> groups.filterByName("menu")
                .forEachClient((group, builder) -> builder.requestInterceptor(new TokenRelay()));
    }
}
