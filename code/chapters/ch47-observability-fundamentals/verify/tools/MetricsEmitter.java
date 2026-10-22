import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Serves one service's order metrics in the Prometheus text format with a configurable number of label values, to
 * show what cardinality costs: a counter and a latency histogram (10 buckets with +Inf) per route (5) × status (4) ×
 * restaurant ({@code restaurants}). With one restaurant that is 20 label combinations; with 1,000 it is 20,000, each
 * of them 13 time series (counter, 10 buckets, sum, count).
 *
 * Usage: java MetricsEmitter port restaurants
 */
public final class MetricsEmitter {

    static final String[] ROUTES = {"/orders", "/orders/{id}", "/menus/{id}", "/payments", "/riders/{id}/location"};
    static final String[] STATUSES = {"200", "201", "404", "503"};
    static final String[] LE = {"0.005", "0.01", "0.025", "0.05", "0.1", "0.25", "0.5", "1", "2.5", "+Inf"};

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(args[0]);
        int restaurants = Integer.parseInt(args[1]);
        int combos = ROUTES.length * STATUSES.length * restaurants;
        String[] labels = new String[combos];
        long[][] counts = new long[combos][LE.length];
        double[] sums = new double[combos];
        int c = 0;
        for (String r : ROUTES) {
            for (String s : STATUSES) {
                for (int i = 0; i < restaurants; i++) {
                    labels[c++] = "route=\"" + r + "\",status=\"" + s + "\"" + (restaurants > 1 ? ",restaurant_id=\"r-" + (10_000 + i) + "\"" : "");
                }
            }
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 16);
        server.createContext("/metrics", ex -> {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            StringBuilder b = new StringBuilder(combos * 14 * 90);
            b.append("# HELP foodfleet_orders_total Orders handled.\n# TYPE foodfleet_orders_total counter\n");
            for (int i = 0; i < combos; i++) {
                int n = rnd.nextInt(3);
                for (int k = 0; k < n; k++) {
                    int bucket = rnd.nextInt(LE.length);
                    for (int j = bucket; j < LE.length; j++) {
                        counts[i][j]++;
                    }
                    sums[i] += 0.002 * (1 << Math.min(bucket, 10));
                }
                b.append("foodfleet_orders_total{").append(labels[i]).append("} ").append(counts[i][LE.length - 1]).append('\n');
            }
            b.append("# HELP foodfleet_order_duration_seconds Order latency.\n# TYPE foodfleet_order_duration_seconds histogram\n");
            for (int i = 0; i < combos; i++) {
                for (int j = 0; j < LE.length; j++) {
                    b.append("foodfleet_order_duration_seconds_bucket{").append(labels[i]).append(",le=\"").append(LE[j])
                            .append("\"} ").append(counts[i][j]).append('\n');
                }
                b.append("foodfleet_order_duration_seconds_sum{").append(labels[i]).append("} ").append(sums[i]).append('\n');
                b.append("foodfleet_order_duration_seconds_count{").append(labels[i]).append("} ")
                        .append(counts[i][LE.length - 1]).append('\n');
            }
            byte[] body = b.toString().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream o = ex.getResponseBody()) {
                o.write(body);
            }
        });
        server.start();
    }
}
