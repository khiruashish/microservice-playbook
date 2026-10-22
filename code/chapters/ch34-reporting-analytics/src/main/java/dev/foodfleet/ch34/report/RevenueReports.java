package dev.foodfleet.ch34.report;

import dev.foodfleet.ch34.freshness.Freshness;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Reads only the reporting store: no calls to other services, no load on their databases. */
@Service
public class RevenueReports {

    public record DayRevenue(LocalDate day, long deliveredOrders, long revenuePaise) {
    }

    public record RestaurantRevenue(long restaurantId, long deliveredOrders, long revenuePaise) {
    }

    public record MonthRevenue(String city, LocalDate month, long deliveredOrders, long revenuePaise) {
    }

    public record Report<T>(List<T> rows, Freshness freshness) {
    }

    static final String DAILY = """
            SELECT business_day, sum(delivered_orders) AS orders, sum(revenue_paise) AS revenue
              FROM daily_restaurant_revenue
             WHERE city = :city AND business_day BETWEEN :from AND :to
             GROUP BY business_day
             ORDER BY business_day""";

    static final String TOP_RESTAURANTS = """
            SELECT restaurant_id, sum(delivered_orders) AS orders, sum(revenue_paise) AS revenue
              FROM daily_restaurant_revenue
             WHERE city = :city AND business_day BETWEEN :from AND :to
             GROUP BY restaurant_id
             ORDER BY revenue DESC, restaurant_id
             LIMIT :limit""";

    static final String MONTHLY = """
            SELECT city, month, delivered_orders, revenue_paise
              FROM city_monthly_revenue
             ORDER BY month, city""";

    private final JdbcClient jdbc;
    private final Clock clock;
    private final Duration objective;

    public RevenueReports(JdbcClient jdbc, Clock clock, @Value("${app.reporting.freshness-objective:5m}") Duration objective) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.objective = objective;
    }

    public Report<DayRevenue> daily(String city, LocalDate from, LocalDate to) {
        var rows = jdbc.sql(DAILY).param("city", city).param("from", from).param("to", to)
                .query((rs, n) -> new DayRevenue(rs.getObject(1, LocalDate.class), rs.getLong(2), rs.getLong(3)))
                .list();
        return new Report<>(rows, freshness());
    }

    public Report<RestaurantRevenue> topRestaurants(String city, LocalDate from, LocalDate to, int limit) {
        var rows = jdbc.sql(TOP_RESTAURANTS).param("city", city).param("from", from).param("to", to)
                .param("limit", limit)
                .query((rs, n) -> new RestaurantRevenue(rs.getLong(1), rs.getLong(2), rs.getLong(3))).list();
        return new Report<>(rows, freshness());
    }

    /** From the materialized view: as fresh as its last refresh, which the freshness here doesn't capture. */
    public List<MonthRevenue> monthly() {
        return jdbc.sql(MONTHLY)
                .query((rs, n) -> new MonthRevenue(rs.getString(1), rs.getObject(2, LocalDate.class), rs.getLong(3),
                        rs.getLong(4)))
                .list();
    }

    public Freshness freshness() {
        Timestamp last = jdbc.sql("SELECT last_event_at FROM projection_status WHERE projection = 'order-facts'")
                .query(Timestamp.class).single();
        return Freshness.of(last == null ? null : last.toInstant(), clock.instant(), objective);
    }
}
