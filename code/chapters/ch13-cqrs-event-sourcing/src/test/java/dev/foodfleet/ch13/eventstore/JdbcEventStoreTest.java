package dev.foodfleet.ch13.eventstore;

import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

class JdbcEventStoreTest extends EventStoreContract {

    @Override
    EventStore store() {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("events-" + UUID.randomUUID()).addScript("event-store.sql").build();
        return new JdbcEventStore(JdbcClient.create(db), new TransactionTemplate(new DataSourceTransactionManager(db)),
                Clock.systemUTC());
    }
}
