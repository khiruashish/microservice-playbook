package dev.foodfleet.ch62;

import dev.foodfleet.ch62.PostgresExtension.TestDatabase;
import dev.foodfleet.ch62.coupon.CouponRedemptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The coupon rule against the database production runs: PostgreSQL in a container, a fresh database per test. */
@Tag("postgres")
@ExtendWith(PostgresExtension.class)
class CouponRedemptionsPostgresTest {

    @Test
    void secondRedemptionByTheSameCustomerIsRefused(TestDatabase db) {
        assertTrue(CouponRedemptions.redeemOnConflict(db::open, "c-1", "FIRST20", 1));
        assertFalse(CouponRedemptions.redeemOnConflict(db::open, "c-1", "FIRST20", 2));
        assertTrue(CouponRedemptions.redeemOnConflict(db::open, "c-2", "FIRST20", 3));
    }

    @Test
    void eachTestStartsWithAnEmptyTable(TestDatabase db) throws Exception {
        try (var c = db.open(); ResultSet rs = c.createStatement().executeQuery("select count(*) from coupon_redemptions")) {
            rs.next();
            assertEquals(0, rs.getLong(1));
        }
        assertTrue(CouponRedemptions.redeemOnConflict(db::open, "c-1", "FIRST20", 1));
    }

    /** The test a single-threaded suite never runs: a double-tap. Exactly one request wins, none fails. */
    @Test
    void concurrentRedemptionsHaveExactlyOneWinner(TestDatabase db) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (int round = 0; round < 20; round++) {
                String customer = "c-" + round;
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Boolean>> results = new ArrayList<>();
                for (int t = 0; t < 8; t++) {
                    long order = t;
                    results.add(pool.submit(() -> {
                        go.await();
                        return CouponRedemptions.redeemOnConflict(db::open, customer, "FIRST20", order);
                    }));
                }
                go.countDown();
                int winners = 0;
                for (Future<Boolean> f : results) {
                    winners += f.get() ? 1 : 0;
                }
                assertEquals(1, winners, "round " + round);
            }
        } finally {
            pool.shutdown();
        }
    }
}
