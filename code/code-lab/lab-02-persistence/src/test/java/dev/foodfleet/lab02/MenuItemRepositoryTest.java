package dev.foodfleet.lab02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The repository and service against PostgreSQL 16, the production engine, never H2 (ADR-062): Flyway applies the
 * real migrations, so these tests also prove the migrations and the entities agree.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(MenuService.class)
class MenuItemRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.13");

    @Autowired
    MenuItemRepository items;

    @Autowired
    MenuService menus;

    @Test
    void migrationsSeedTheMenu() {
        assertThat(menus.menu("r-1", false)).extracting(MenuItem::name)
                .containsExactly("Paneer tikka", "Chicken biryani", "Masala dosa");
    }

    @Test
    void vegetarianFilterRunsInTheDatabase() {
        assertThat(menus.menu("r-1", true)).extracting(MenuItem::name).containsExactly("Paneer tikka", "Masala dosa");
    }

    @Test
    void repricingIncrementsTheVersion() {
        MenuItemEntity dosa = items.findByRestaurantIdOrderById("r-1").get(2);
        MenuItem repriced = menus.reprice(dosa.getId(), dosa.getVersion(), 15900);
        assertThat(repriced.pricePaise()).isEqualTo(15900);
        assertThat(repriced.version()).isEqualTo(dosa.getVersion() + 1);
    }

    @Test
    void aStaleVersionIsRefused() {
        MenuItemEntity tikka = items.findByRestaurantIdOrderById("r-1").get(0);
        long seen = tikka.getVersion();
        menus.reprice(tikka.getId(), seen, 25900);                       // someone else's edit lands first
        assertThatThrownBy(() -> menus.reprice(tikka.getId(), seen, 23900))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void theDatabaseRejectsAFreeDishEvenIfTheCodeDidnt() {
        assertThatThrownBy(() -> items.saveAndFlush(new MenuItemEntity("r-1", "Water", 0, true)))
                .hasMessageContaining("price_paise");
    }
}
