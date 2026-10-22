package dev.foodfleet.ch64.e2e;

import dev.foodfleet.ch64.app.Web;
import dev.foodfleet.ch64.json.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FoodFleet's end-to-end journeys, run against order-service's URL in some environment. The same five run as
 * component tests (order-service alone, its dependencies stubbed) and as E2E tests (everything deployed).
 */
public final class Journeys {

    public record Outcome(String journey, boolean passed, String detail) {
    }

    /** How a test waits for something asynchronous. */
    public interface Wait {
        /** Calls {@code check} until it answers true or the wait gives up; returns its last answer. */
        boolean until(java.util.function.BooleanSupplier check) throws InterruptedException;

        static Wait sleep(long millis) {
            return check -> {
                Thread.sleep(millis);
                return check.getAsBoolean();
            };
        }

        static Wait poll(long intervalMillis, long timeoutMillis) {
            return check -> {
                long end = System.nanoTime() + timeoutMillis * 1_000_000;
                while (true) {
                    if (check.getAsBoolean()) {
                        return true;
                    }
                    if (System.nanoTime() > end) {
                        return false;
                    }
                    Thread.sleep(intervalMillis);
                }
            };
        }
    }

    private Journeys() {
    }

    /**
     * The suite. {@code uniqueData}: a new customer per run, as a test should; otherwise the fixed "e2e-customer" so
     * many suites written against a shared staging environment use.
     */
    public static List<Outcome> run(String order, boolean uniqueData, Wait wait) throws InterruptedException {
        String customer = uniqueData ? "e2e-" + UUID.randomUUID() : "e2e-customer";
        List<Outcome> out = new ArrayList<>();
        Web.Response first = place(order, customer, "paneer-tikka", "FIRST20");
        out.add(check("first order with FIRST20 gets 20% off", first.status() == 201 && "25.90".equals(field(first, "discount")), first));
        Web.Response second = place(order, customer, "dal-makhani", "FIRST20");
        out.add(check("a second FIRST20 order gets no discount", second.status() == 201 && "0.00".equals(field(second, "discount")), second));
        Web.Response biryani = place(order, customer, "biryani-of-the-day", null);
        out.add(check("the biryani of the day can be ordered", biryani.status() == 201, biryani));
        if (first.status() == 201) {
            String id = field(first, "id");
            Web.Response[] last = new Web.Response[1];
            boolean assigned = wait.until(() -> {
                last[0] = Web.call("GET", order + "/orders/" + id, null);
                return last[0].status() == 200 && "COURIER_ASSIGNED".equals(field(last[0], "delivery"));
            });
            out.add(check("the order gets a courier", assigned, last[0]));
            Web.Response read = Web.call("GET", order + "/orders/" + id, null);
            out.add(check("the order can be read back", read.status() == 200 && field(first, "total").equals(field(read, "total")), read));
        } else {
            out.add(new Outcome("the order gets a courier", false, "no order to check"));
            out.add(new Outcome("the order can be read back", false, "no order to check"));
        }
        return out;
    }

    /** Only the courier journey: place an order, wait, check. */
    public static Outcome courier(String order, Wait wait) throws InterruptedException {
        Web.Response placed = place(order, "e2e-" + UUID.randomUUID(), "paneer-tikka", null);
        if (placed.status() != 201) {
            return new Outcome("the order gets a courier", false, placed.body());
        }
        String id = field(placed, "id");
        boolean assigned = wait.until(() -> "COURIER_ASSIGNED".equals(field(Web.call("GET", order + "/orders/" + id, null), "delivery")));
        return new Outcome("the order gets a courier", assigned, assigned ? "" : "still PENDING");
    }

    static Web.Response place(String order, String customer, String item, String coupon) {
        String body = "{\"customerId\":\"" + customer + "\",\"restaurantId\":7,\"itemId\":\"" + item + "\""
                + (coupon == null ? "" : ",\"coupon\":\"" + coupon + "\"") + "}";
        return Web.call("POST", order + "/orders", body);
    }

    static String field(Web.Response r, String name) {
        try {
            Object v = Json.object(Json.parse(r.body())).get(name);
            return v == null ? null : v instanceof java.math.BigDecimal d ? d.toPlainString() : String.valueOf(v);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Outcome check(String journey, boolean ok, Web.Response r) {
        return new Outcome(journey, ok, ok ? "" : r.status() + " " + (r.body().length() > 80 ? r.body().substring(0, 80) : r.body()));
    }

    /** For tests: the discount a response carries. */
    public static Map<String, Object> body(Web.Response r) {
        return Json.object(Json.parse(r.body()));
    }
}
