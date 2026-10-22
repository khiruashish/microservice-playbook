package dev.foodfleet.ch64.app;

import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch64.json.Json;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * order-service: places an order (reserve the dish at menu-service, apply FIRST20 once per customer, store the
 * order, ask delivery-service for a courier) and shows it with its delivery status.
 */
public final class OrderApp {

    public interface Db {
        Connection open() throws SQLException;
    }

    private OrderApp() {
    }

    public static final String SCHEMA = """
            create table if not exists orders (id bigserial primary key, customer_id varchar(64) not null, item_id varchar(64) not null,
                discount numeric(10, 2) not null, total numeric(10, 2) not null);
            create table if not exists coupon_redemptions (customer_id varchar(64) not null, coupon varchar(32) not null,
                primary key (customer_id, coupon));
            """;

    public static HttpServer start(int port, String menu, String delivery, Db db) throws IOException {
        try (Connection c = db.open(); Statement s = c.createStatement()) {
            s.execute(SCHEMA);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return Web.serve(port, (method, path, body) -> {
            String[] p = path.split("/"); // /orders[/{id}]
            if (method.equals("POST") && p.length == 2 && p[1].equals("orders")) {
                return place(Json.object(Json.parse(body)), menu, delivery, db);
            }
            if (method.equals("GET") && p.length == 3 && p[1].equals("orders")) {
                return show(Long.parseLong(p[2]), delivery, db);
            }
            return Web.json(404, Map.of("error", "not found"));
        });
    }

    static Web.Response place(Map<String, Object> req, String menu, String delivery, Db db) throws SQLException {
        String customer = (String) req.get("customerId");
        String item = (String) req.get("itemId");
        Web.Response price = Web.call("GET", menu + "/restaurants/7/items/" + item, null);
        if (price.status() != 200) {
            return Web.json(400, Map.of("error", "unknown item"));
        }
        Web.Response reserved = Web.call("POST", menu + "/restaurants/7/items/" + item + "/reserve", null);
        if (reserved.status() == 409) {
            return Web.json(409, Map.of("error", "sold out"));
        }
        BigDecimal subtotal = new BigDecimal((String) price.json().get("price"));
        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        long id;
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            if ("FIRST20".equals(req.get("coupon"))) {
                try (PreparedStatement ps = c.prepareStatement(
                        "insert into coupon_redemptions (customer_id, coupon) values (?, 'FIRST20') on conflict do nothing")) {
                    ps.setString(1, customer);
                    if (ps.executeUpdate() == 1) {
                        discount = subtotal.multiply(new BigDecimal("0.20")).min(new BigDecimal("100")).setScale(2, RoundingMode.HALF_EVEN);
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("insert into orders (customer_id, item_id, discount, total) values (?, ?, ?, ?) returning id")) {
                ps.setString(1, customer);
                ps.setString(2, item);
                ps.setBigDecimal(3, discount);
                ps.setBigDecimal(4, subtotal.subtract(discount));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    id = rs.getLong(1);
                }
            }
            c.commit();
        }
        Web.call("POST", delivery + "/deliveries", Json.write(Map.of("orderId", String.valueOf(id))));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", BigDecimal.valueOf(id));
        out.put("discount", discount.toPlainString());
        out.put("total", subtotal.subtract(discount).toPlainString());
        return Web.json(201, out);
    }

    static Web.Response show(long id, String delivery, Db db) throws SQLException {
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement("select customer_id, item_id, discount, total from orders where id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Web.json(404, Map.of("error", "no such order"));
                }
                Web.Response d = Web.call("GET", delivery + "/deliveries/" + id, null);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("id", BigDecimal.valueOf(id));
                out.put("customerId", rs.getString(1));
                out.put("itemId", rs.getString(2));
                out.put("discount", rs.getBigDecimal(3).toPlainString());
                out.put("total", rs.getBigDecimal(4).toPlainString());
                out.put("delivery", d.status() == 200 ? d.json().get("status") : "UNKNOWN");
                return Web.json(200, out);
            }
        }
    }

    /** A JDBC URL as the database. */
    public static Db jdbc(String url) {
        return () -> DriverManager.getConnection(url, "postgres", "");
    }
}
