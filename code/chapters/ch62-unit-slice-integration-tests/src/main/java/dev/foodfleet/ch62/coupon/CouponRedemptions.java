package dev.foodfleet.ch62.coupon;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * FIRST20 may be redeemed once per customer. Two ways to write "insert unless it exists": with PostgreSQL's
 * {@code ON CONFLICT}, atomic in the database; and the portable way H2 also accepts, check then insert.
 */
public final class CouponRedemptions {

    /** Opens a connection; a pool in production. */
    public interface Connections {
        Connection open() throws SQLException;
    }

    private CouponRedemptions() {
    }

    /** PostgreSQL: one statement; concurrent callers can't both win. */
    public static boolean redeemOnConflict(Connections db, String customer, String coupon, long orderId) {
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(
                "insert into coupon_redemptions (customer_id, coupon, order_id) values (?, ?, ?) on conflict (customer_id, coupon) do nothing")) {
            ps.setString(1, customer);
            ps.setString(2, coupon);
            ps.setLong(3, orderId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /** Portable: look, then insert. Runs on H2 too; between the two statements another caller can insert. */
    public static boolean redeemCheckThenInsert(Connections db, String customer, String coupon, long orderId) {
        try (Connection c = db.open()) {
            try (PreparedStatement ps = c.prepareStatement("select 1 from coupon_redemptions where customer_id = ? and coupon = ?")) {
                ps.setString(1, customer);
                ps.setString(2, coupon);
                if (ps.executeQuery().next()) {
                    return false;
                }
            }
            try (PreparedStatement ps = c.prepareStatement("insert into coupon_redemptions (customer_id, coupon, order_id) values (?, ?, ?)")) {
                ps.setString(1, customer);
                ps.setString(2, coupon);
                ps.setLong(3, orderId);
                return ps.executeUpdate() == 1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
