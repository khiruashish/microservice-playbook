package dev.foodfleet.ch62;

import dev.foodfleet.ch62.coupon.CouponRedemptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an in-memory H2 in PostgreSQL mode can and can't stand in for. These tests pin the findings: if a later H2
 * learns ON CONFLICT, the first one fails and the chapter's advice needs revisiting.
 */
@Tag("h2")
class H2CompatibilityTest {

    static final AtomicInteger N = new AtomicInteger();
    String url;

    @BeforeEach
    void freshDatabase() throws Exception {
        url = "jdbc:h2:mem:h2c" + N.incrementAndGet() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
        try (Connection c = open()) {
            c.createStatement().execute(Files.readString(Path.of(System.getProperty("ff.schema", "src/main/resources/db/schema.sql"))));
        }
    }

    Connection open() throws java.sql.SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }

    @Test
    void h2RejectsTheProductionStatement() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> CouponRedemptions.redeemOnConflict(this::open, "c-1", "FIRST20", 1));
        assertTrue(e.getMessage().contains("Syntax error"), e.getMessage());
    }

    /** Passes on H2, and would pass on PostgreSQL too: single-threaded tests can't see the race in this code. */
    @Test
    void checkThenInsertPassesSingleThreaded() {
        assertTrue(CouponRedemptions.redeemCheckThenInsert(this::open, "c-1", "FIRST20", 1));
        assertFalse(CouponRedemptions.redeemCheckThenInsert(this::open, "c-1", "FIRST20", 2));
    }
}
