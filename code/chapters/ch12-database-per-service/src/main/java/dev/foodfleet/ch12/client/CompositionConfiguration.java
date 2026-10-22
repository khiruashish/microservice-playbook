package dev.foodfleet.ch12.client;

import dev.foodfleet.ch12.composition.OrderHistoryComposer;
import dev.foodfleet.ch12.composition.Ports;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/** Adapts the HTTP clients to the composer's ports and wires the composer. */
@Configuration(proxyBeanMethods = false)
class CompositionConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ImportHttpServices(group = "ordering", types = HttpClients.OrderingApi.class)
    static class Ordering {
    }

    @Configuration(proxyBeanMethods = false)
    @ImportHttpServices(group = "catalog", types = HttpClients.CatalogApi.class)
    static class Catalog {
    }

    @Configuration(proxyBeanMethods = false)
    @ImportHttpServices(group = "delivery", types = HttpClients.DeliveryApi.class)
    static class Delivery {
    }

    @Bean
    OrderHistoryComposer orderHistoryComposer(HttpClients.OrderingApi ordering, HttpClients.CatalogApi catalog,
                                              HttpClients.DeliveryApi delivery) {
        Ports.Ordering orderingPort = ordering::recent;
        Ports.Catalog catalogPort = ids -> catalog.byIds(List.copyOf(ids)).stream()
                .collect(Collectors.toMap(Ports.RestaurantInfo::restaurantId, Function.identity(), (a, b) -> a));
        Ports.Delivery deliveryPort = ids -> delivery.byOrderIds(List.copyOf(ids)).stream()
                .collect(Collectors.toMap(Ports.DeliveryInfo::orderId, Function.identity(), (a, b) -> a));
        return new OrderHistoryComposer(orderingPort, catalogPort, deliveryPort, Duration.ofMillis(500));
    }
}
