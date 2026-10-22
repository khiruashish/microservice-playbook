package dev.foodfleet.ch05.ordering.infrastructure;

import dev.foodfleet.ch05.catalog.CatalogApi;
import dev.foodfleet.ch05.catalog.InMemoryCatalogApi;
import dev.foodfleet.ch05.ordering.application.DeliveryFeeQuote;
import dev.foodfleet.ch05.ordering.application.OrderApplicationService;
import dev.foodfleet.ch05.ordering.domain.DomainEventPublisher;
import dev.foodfleet.ch05.ordering.domain.MenuCatalog;
import dev.foodfleet.ch05.ordering.domain.Money;
import dev.foodfleet.ch05.ordering.domain.OrderRepository;
import dev.foodfleet.ch05.ordering.domain.PricingPolicy;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root: the only place that knows which adapter implements which port. Domain and
 * application classes carry no Spring annotations, so they stay plain Java and trivially testable.
 */
@Configuration(proxyBeanMethods = false)
public class OrderingConfiguration {

    @Bean
    CatalogApi catalogApi() {
        return new InMemoryCatalogApi(); // production: an HTTP client for restaurant-service
    }

    @Bean
    MenuCatalog menuCatalog(CatalogApi catalogApi) {
        return new CatalogAntiCorruptionLayer(catalogApi, new CatalogTranslator());
    }

    @Bean
    OrderRepository orderRepository() {
        return new InMemoryOrderRepository();
    }

    /** Flat fee for chapter 5; production calls zone-service (chapter 4) through an HTTP client. */
    @Bean
    DeliveryFeeQuote deliveryFeeQuote(@Value("${foodfleet.ordering.flat-delivery-fee-paise:3500}") long feePaise) {
        Money fee = Money.ofPaise(feePaise);
        return address -> fee;
    }

    /** Domain events become Spring application events. Chapter 15 swaps this for a transactional outbox. */
    @Bean
    DomainEventPublisher domainEventPublisher(ApplicationEventPublisher spring) {
        return events -> events.forEach(spring::publishEvent);
    }

    @Bean
    OrderApplicationService orderApplicationService(OrderRepository orders, MenuCatalog catalog,
                                                    DeliveryFeeQuote fees, DomainEventPublisher events) {
        return new OrderApplicationService(orders, catalog, fees, new PricingPolicy(), events, Clock.systemUTC());
    }
}
