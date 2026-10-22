package dev.foodfleet.ch19.app.ordering;

import dev.foodfleet.ch19.app.catalog.MenuItemUpdated;
import java.util.function.Consumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cloud Stream's functional model: a {@code Consumer} bean is bound to a topic by
 * configuration ({@code menuItemUpdated-in-0}). The code doesn't name Kafka, topics or groups.
 */
@Configuration(proxyBeanMethods = false)
class MenuEventsConsumer {

    @Bean
    Consumer<MenuItemUpdated> menuItemUpdated(MenuReplica replica) {
        return replica::apply;
    }
}
