package dev.foodfleet.ch21.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch21.paging.Cursor;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The keyset query and the optimistic update on real PostgreSQL. Skipped without Docker. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class JdbcOrderStoreContainerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired OrderStore store;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void orders() {
        jdbc.sql("truncate orders").update();
        jdbc.sql("""
                insert into orders (id, customer_id, restaurant, total_paise, status, placed_at) values
                 ('o-1','c-1','Meghana Foods',45000,'DELIVERED','2026-10-01 12:00:00+00'),
                 ('o-2','c-1','Truffles',32000,'DELIVERED','2026-10-02 13:00:00+00'),
                 ('o-3','c-1','Vidyarthi Bhavan',18000,'DELIVERED','2026-10-03 08:30:00+00'),
                 ('o-4','c-1','Toit',96000,'DELIVERED','2026-10-03 08:30:00+00'),
                 ('o-5','c-1','CTR',15000,'DELIVERED','2026-10-03 08:30:00+00'),
                 ('o-6','c-2','Truffles',30000,'DELIVERED','2026-10-04 09:00:00+00')
                """).update();
    }

    @Test
    void pagesAcrossOrdersPlacedInTheSameInstantWithoutGapsOrRepeats() {
        var first = store.page("c-1", Optional.empty(), 2);
        var second = store.page("c-1", Optional.of(first.getLast().cursor()), 2);
        var third = store.page("c-1", Optional.of(second.getLast().cursor()), 2);

        assertThat(first).extracting(OrderSummary::id).containsExactly("o-5", "o-4");
        assertThat(second).extracting(OrderSummary::id).containsExactly("o-3", "o-2");
        assertThat(third).extracting(OrderSummary::id).containsExactly("o-1");
    }

    @Test
    void theCursorSurvivesTheRoundTripThroughText() {
        var first = store.page("c-1", Optional.empty(), 1);
        Cursor fromClient = Cursor.decode(first.getFirst().cursor().encode());

        assertThat(store.page("c-1", Optional.of(fromClient), 1)).extracting(OrderSummary::id).containsExactly("o-4");
    }

    @Test
    void anUpdateWithAStaleVersionChangesNothing() {
        store.updateDeliveryInstructions("o-2", 1, "Ring twice");

        assertThatThrownBy(() -> store.updateDeliveryInstructions("o-2", 1, "Leave at gate"))
                .isInstanceOf(OrderStore.VersionConflict.class);
        assertThat(store.find("o-2").orElseThrow().deliveryInstructions()).isEqualTo("Ring twice");
        assertThat(store.find("o-2").orElseThrow().version()).isEqualTo(2L);
    }

    @Test
    void placedAtKeepsItsInstant() {
        assertThat(store.find("o-1").orElseThrow().placedAt()).isEqualTo(Instant.parse("2026-10-01T12:00:00Z"));
    }
}
