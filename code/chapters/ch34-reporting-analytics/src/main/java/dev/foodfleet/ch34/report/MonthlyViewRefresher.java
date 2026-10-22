package dev.foodfleet.ch34.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refreshes the monthly view without blocking its readers. A plain REFRESH takes an exclusive lock for the whole
 * recomputation (measured on ~0.9 M rows: every reader waited 3.0-3.4 s); CONCURRENTLY builds the new contents
 * alongside and applies the differences, so reads continue (longest read 26-28 ms), at 7x the refresh time.
 */
@Component
public class MonthlyViewRefresher {

    private static final Logger log = LoggerFactory.getLogger(MonthlyViewRefresher.class);
    private final JdbcClient jdbc;

    public MonthlyViewRefresher(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${app.reporting.monthly-refresh:5m}", initialDelayString = "${app.reporting.monthly-refresh:5m}")
    @Transactional
    public void refresh() {
        jdbc.sql("SET LOCAL lock_timeout = '5s'").update();              // never queue behind a long report
        jdbc.sql("SET LOCAL statement_timeout = '10min'").update();
        long start = System.nanoTime();
        jdbc.sql("REFRESH MATERIALIZED VIEW CONCURRENTLY city_monthly_revenue").update();
        log.info("city_monthly_revenue refreshed in {} ms", (System.nanoTime() - start) / 1_000_000);
    }
}
