package dev.foodfleet.ch10.bff.client;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/** One HTTP service group per downstream service, so each gets its own URL and timeouts. */
@Configuration(proxyBeanMethods = false)
class ClientConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Configuration(proxyBeanMethods = false)
    @ImportHttpServices(group = "ordering", types = Downstream.OrderingClient.class)
    static class Ordering {
    }

    @Configuration(proxyBeanMethods = false)
    @ImportHttpServices(group = "eta", types = Downstream.EtaClient.class)
    static class Eta {
    }

    @Configuration(proxyBeanMethods = false)
    @ImportHttpServices(group = "catalog", types = Downstream.CatalogClient.class)
    static class Catalog {
    }

    @Configuration(proxyBeanMethods = false)
    @ImportHttpServices(group = "dispatch", types = Downstream.DispatchClient.class)
    static class Dispatch {
    }
}
