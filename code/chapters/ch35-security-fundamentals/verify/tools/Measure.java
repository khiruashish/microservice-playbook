import dev.foodfleet.ch35.auth.LoginThrottle;
import dev.foodfleet.ch35.auth.PasswordHasher;
import dev.foodfleet.ch35.identity.Caller;
import dev.foodfleet.ch35.identity.Role;
import dev.foodfleet.ch35.orders.Order;
import dev.foodfleet.ch35.orders.OrderAccessPolicy;
import dev.foodfleet.ch35.orders.OrderAccessPolicy.Action;
import dev.foodfleet.ch35.orders.OrderStatus;
import dev.foodfleet.ch35.threatmodel.CheckoutThreatModel;
import dev.foodfleet.ch35.threatmodel.ThreatReport;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The chapter's measurements, JDK only, over the module's own classes (compiled by run.sh):
 *   authz        every (caller, action, order) decision of OrderAccessPolicy against a rule table written
 *                separately from it, in the business's words
 *   throttle     how many password checks LoginThrottle lets through per account in an hour of simulated time
 *   hashrate     the cost of a guess against a stored hash: unsalted SHA-256 vs PBKDF2 at 600,000 iterations
 *   threatmodel  the checkout threat model's STRIDE summary
 */
public class Measure {

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "authz" -> authz();
            case "throttle" -> throttle();
            case "hashrate" -> hashrate();
            case "threatmodel" -> System.out.print(ThreatReport.text(CheckoutThreatModel.build()));
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    // ------------------------------------------------------------------ authorization

    /** The business rules, restated independently of OrderAccessPolicy's code. */
    static boolean expected(Caller c, Action a, Order o) {
        boolean ownsOrder = c.has(Role.CUSTOMER) && c.id().equals(o.customerId());
        boolean staff = c.has(Role.SUPPORT) || c.has(Role.ADMIN);
        boolean open = o.status() == OrderStatus.PLACED || o.status() == OrderStatus.PREPARING;
        return switch (a) {
            case READ -> ownsOrder || staff
                    || (c.has(Role.RESTAURANT_OWNER) && c.restaurantIds().contains(o.restaurantId()))
                    || (c.has(Role.RIDER) && c.id().equals(o.riderId()));
            case UPDATE_DETAILS, CANCEL -> ownsOrder && open;
            case REFUND -> staff && o.status() == OrderStatus.DELIVERED;
            case EXPORT_ALL -> c.has(Role.ADMIN);
        };
    }

    static void authz() {
        List<Caller> callers = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            callers.add(new Caller("c" + i, Set.of(Role.CUSTOMER), Set.of()));
        }
        for (int i = 0; i < 4; i++) {
            callers.add(new Caller("owner" + i, Set.of(Role.RESTAURANT_OWNER),
                    Set.of((long) i * 5 + 1, (long) i * 5 + 2, (long) i * 5 + 3, (long) i * 5 + 4, (long) i * 5 + 5)));
            callers.add(new Caller("rider" + i, Set.of(Role.RIDER), Set.of()));
        }
        callers.add(new Caller("support", Set.of(Role.SUPPORT), Set.of()));
        callers.add(new Caller("admin", Set.of(Role.ADMIN), Set.of()));
        // a caller with two roles: a restaurant owner who also orders food
        callers.add(new Caller("c1", Set.of(Role.CUSTOMER, Role.RESTAURANT_OWNER), Set.of(3L)));

        List<Order> orders = new ArrayList<>();
        OrderStatus[] statuses = OrderStatus.values();
        long id = 1;
        for (int customer = 1; customer <= 20; customer++) {
            for (OrderStatus s : statuses) {
                orders.add(new Order(id, "c" + customer, 1 + (id % 20), "rider" + (id % 4), s, 30000,
                        "addr", "+919800000000", null, 1, "tok"));
                id++;
            }
        }
        OrderAccessPolicy policy = new OrderAccessPolicy();
        long decisions = 0;
        long allowed = 0;
        long mismatches = 0;
        long crossCustomerAllowed = 0;
        for (Caller c : callers) {
            for (Order o : orders) {
                for (Action a : Action.values()) {
                    boolean got = policy.allows(c, a, o);
                    decisions++;
                    if (got) {
                        allowed++;
                    }
                    if (got != expected(c, a, o)) {
                        mismatches++;
                        System.out.println("MISMATCH " + c.id() + " " + a + " order " + o.id() + ": policy " + got);
                    }
                    if (got && c.roles().equals(Set.of(Role.CUSTOMER)) && !c.id().equals(o.customerId())) {
                        crossCustomerAllowed++;
                    }
                }
            }
        }
        System.out.printf("%d callers x %d orders x %d actions = %,d decisions%n", callers.size(), orders.size(),
                Action.values().length, decisions);
        System.out.printf("  allowed %,d, denied %,d%n", allowed, decisions - allowed);
        System.out.printf("  disagreements with the rule table: %d%n", mismatches);
        System.out.printf("  a customer allowed anything on another customer's order: %d%n", crossCustomerAllowed);
    }

    // ------------------------------------------------------------------ throttle

    static final class SimClock extends Clock {
        Instant now = Instant.parse("2025-04-14T00:00:00Z");
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId z) { return this; }
    }

    /** A client that retries the moment it is allowed, for one simulated hour; every check fails. */
    static long checksPerHour(int sources) {
        SimClock clock = new SimClock();
        LoginThrottle throttle = LoginThrottle.standard(clock);
        Instant end = clock.now.plus(Duration.ofHours(1));
        long checks = 0;
        int next = 0;
        while (clock.now.isBefore(end)) {
            String source = "10.0." + (next % sources) / 256 + "." + (next % sources) % 256;
            next++;
            LoginThrottle.Verdict v = throttle.check("asha@example.com", source);
            if (v.allowed()) {
                checks++;
                throttle.failed("asha@example.com", source);
                clock.now = clock.now.plusMillis(135);         // one PBKDF2 verification
            } else {
                clock.now = clock.now.plus(v.retryAfter());
            }
        }
        return checks;
    }

    static void throttle() {
        long unthrottled = Duration.ofHours(1).toMillis() / 135;  // bounded only by one core verifying hashes (~135 ms each)
        System.out.println("password checks against one account in one hour:");
        System.out.printf("  no throttle (one core verifying PBKDF2)     %,8d%n", unthrottled);
        System.out.printf("  LoginThrottle, one source address           %,8d%n", checksPerHour(1));
        System.out.printf("  LoginThrottle, 1,000 source addresses       %,8d%n", checksPerHour(1000));
    }

    // ------------------------------------------------------------------ hashing cost

    static void hashrate() throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] pw = "candidate".getBytes(StandardCharsets.UTF_8);
        long n = 0;
        long end = System.nanoTime() + 3_000_000_000L;
        while (System.nanoTime() < end) {
            for (int i = 0; i < 1000; i++) {
                pw[0] = (byte) i;
                sha.digest(pw);
            }
            n += 1000;
        }
        double shaRate = n / 3.0;
        PasswordHasher hasher = new PasswordHasher();
        String stored = hasher.hash("candidate".toCharArray());
        int k = 0;
        long t0 = System.nanoTime();
        while (System.nanoTime() - t0 < 5_000_000_000L) {
            hasher.matches(("x" + k).toCharArray(), stored);
            k++;
        }
        double pbkdfRate = k / ((System.nanoTime() - t0) / 1e9);
        System.out.println("hash computations per second on one CPU core:");
        System.out.printf("  SHA-256, unsalted                 %,14.0f%n", shaRate);
        System.out.printf("  PBKDF2-HMAC-SHA256, 600,000 its.  %,14.1f   (%.0f ms per login)%n", pbkdfRate, 1000 / pbkdfRate);
        System.out.printf("  ratio                             %,14.0fx%n", shaRate / pbkdfRate);
    }
}
