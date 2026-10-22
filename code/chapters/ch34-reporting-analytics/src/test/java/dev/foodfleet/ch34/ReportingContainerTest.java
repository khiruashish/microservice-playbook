package dev.foodfleet.ch34;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch34.projection.OrderEvent;
import dev.foodfleet.ch34.projection.OrderFactProjector;
import dev.foodfleet.ch34.projection.OrderFactProjector.Outcome;
import dev.foodfleet.ch34.report.MonthlyViewRefresher;
import dev.foodfleet.ch34.report.RevenueReports;
import dev.foodfleet.ch34.report.RevenueReports.DayRevenue;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The projection and reports against real PostgreSQL; Kafka is not started (events are applied directly). */
@SpringBootTest(properties = {"spring.kafka.listener.auto-startup=false", "app.reporting.monthly-refresh=1h"})
@Testcontainers(disabledWithoutDocker = true)
class ReportingContainerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired JdbcClient jdbc;
    @Autowired OrderFactProjector projector;
    @Autowired RevenueReports reports;
    @Autowired MonthlyViewRefresher refresher;

    static final LocalDate APR_14 = LocalDate.of(2025, 4, 14);

    static OrderEvent event(long order, int version, String status, long amount, String placedAt) {
        Instant placed = Instant.parse(placedAt);
        return new OrderEvent("e-" + order + "-" + version, order, version, 7, "BLR", status, amount, placed,
                placed.plusSeconds(60L * version));
    }

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE order_facts, daily_restaurant_revenue").update();
        jdbc.sql("UPDATE projection_status SET last_event_at = NULL, applied = 0").update();
    }

    @Test
    void duplicatesAndLateEventsDontChangeTheTotals() {
        projector.apply(event(1, 1, "PLACED", 45000, "2025-04-14T07:00:00Z"));
        projector.apply(event(1, 3, "DELIVERED", 45000, "2025-04-14T07:00:00Z"));

        assertThat(projector.apply(event(1, 3, "DELIVERED", 45000, "2025-04-14T07:00:00Z")))
                .isEqualTo(Outcome.STALE_OR_DUPLICATE);                         // redelivered
        assertThat(projector.apply(event(1, 2, "PREPARING", 45000, "2025-04-14T07:00:00Z")))
                .isEqualTo(Outcome.STALE_OR_DUPLICATE);                         // arrived after version 3

        assertThat(reports.daily("BLR", APR_14, APR_14).rows()).isEqualTo(List.of(new DayRevenue(APR_14, 1, 45000)));
    }

    @Test
    void aRefundTakesTheOrderBackOutOfTheRevenue() {
        projector.apply(event(2, 1, "DELIVERED", 30000, "2025-04-14T08:00:00Z"));
        projector.apply(event(3, 1, "DELIVERED", 20000, "2025-04-14T09:00:00Z"));
        projector.apply(event(2, 2, "REFUNDED", 30000, "2025-04-14T08:00:00Z"));

        assertThat(reports.daily("BLR", APR_14, APR_14).rows()).isEqualTo(List.of(new DayRevenue(APR_14, 1, 20000)));
    }

    @Test
    void ordersCountOnTheCitysBusinessDayNotTheUtcDay() {
        // 20:00 UTC on 13 April is 01:30 on 14 April in Bengaluru
        projector.apply(event(4, 1, "DELIVERED", 50000, "2025-04-13T20:00:00Z"));

        assertThat(reports.daily("BLR", APR_14.minusDays(1), APR_14).rows())
                .isEqualTo(List.of(new DayRevenue(APR_14, 1, 50000)));
    }

    @Test
    void theMonthlyViewShowsTheDailyTotalsAfterARefresh() {
        projector.apply(event(5, 1, "DELIVERED", 10000, "2025-04-14T08:00:00Z"));
        refresher.refresh();

        assertThat(reports.monthly()).isEqualTo(List.of(new RevenueReports.MonthRevenue("BLR", LocalDate.of(2025, 4, 1), 1, 10000)));
    }

    @Test
    void reportsSayHowFreshTheyAre() {
        projector.apply(event(6, 2, "DELIVERED", 10000, "2025-04-14T08:00:00Z"));
        var freshness = reports.daily("BLR", APR_14, APR_14).freshness();
        assertThat(freshness.asOf()).isEqualTo(Instant.parse("2025-04-14T08:02:00Z"));
        assertThat(freshness.stale()).isTrue();                                 // April 2025 is long ago
    }
}
