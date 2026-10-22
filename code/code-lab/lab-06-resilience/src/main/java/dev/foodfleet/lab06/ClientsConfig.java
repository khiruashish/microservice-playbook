package dev.foodfleet.lab06;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/** One client group per destination, configured under spring.http.serviceclient.menu.* (ADR-022). */
@Configuration
@ImportHttpServices(group = "menu", types = MenuClient.class)
public class ClientsConfig {
}
