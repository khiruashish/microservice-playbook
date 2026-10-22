package dev.foodfleet.ch17.adapter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates the tablet gateway's /stats page into the Prometheus text format the platform
 * scrapes everywhere else: names, units (milliseconds to seconds), types, labels.
 *
 * <pre>
 *   legacy                          Prometheus
 *   ORD_ACC=1532                    kitchen_tablet_orders_total{outcome="accepted"} 1532
 *   ORD_REJ=47                      kitchen_tablet_orders_total{outcome="rejected"} 47
 *   LAT_MS_P50=210                  kitchen_tablet_accept_latency_p50_seconds 0.21
 *   LAT_MS_P95=820                  kitchen_tablet_accept_latency_p95_seconds 0.82
 *   TABLETS_ONLINE=37/40            kitchen_tablets_online 37, kitchen_tablets 40
 *   UPTIME_S=86400                  kitchen_tablet_gateway_uptime_seconds 86400
 *   LAST_SYNC=06-10-2026 12:41      kitchen_tablet_last_sync_timestamp_seconds 1791270660
 * </pre>
 * Unknown keys and malformed lines are counted, not fatal: a vendor firmware update shouldn't
 * take the metrics down.
 */
public final class TabletStatsTranslator {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter SYNC_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private record Metric(String help, String type) {
    }

    private static final Map<String, Metric> METRICS = new LinkedHashMap<>();

    static {
        METRICS.put("kitchen_tablet_gateway_up", new Metric("1 if the tablet gateway's stats could be read.", "gauge"));
        METRICS.put("kitchen_tablet_orders_total", new Metric("Orders handled by restaurant tablets.", "counter"));
        METRICS.put("kitchen_tablet_accept_latency_p50_seconds", new Metric("Median time to accept an order.", "gauge"));
        METRICS.put("kitchen_tablet_accept_latency_p95_seconds", new Metric("95th percentile time to accept.", "gauge"));
        METRICS.put("kitchen_tablets_online", new Metric("Tablets currently connected.", "gauge"));
        METRICS.put("kitchen_tablets", new Metric("Tablets registered.", "gauge"));
        METRICS.put("kitchen_tablet_gateway_uptime_seconds", new Metric("Gateway uptime.", "gauge"));
        METRICS.put("kitchen_tablet_last_sync_timestamp_seconds", new Metric("Last menu sync, Unix time.", "gauge"));
        METRICS.put("kitchen_tablet_adapter_unparsed_lines", new Metric("Lines the adapter could not translate.", "gauge"));
    }

    public String translate(String stats) {
        Map<String, StringBuilder> samples = new LinkedHashMap<>();
        METRICS.keySet().forEach(name -> samples.put(name, new StringBuilder()));
        int unparsed = 0;
        for (String raw : stats.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                unparsed++;
                continue;
            }
            String key = line.substring(0, eq).strip().toUpperCase();
            String value = line.substring(eq + 1).strip();
            try {
                switch (key) {
                    case "ORD_ACC" -> add(samples, "kitchen_tablet_orders_total", "{outcome=\"accepted\"}", whole(value));
                    case "ORD_REJ" -> add(samples, "kitchen_tablet_orders_total", "{outcome=\"rejected\"}", whole(value));
                    case "LAT_MS_P50" -> add(samples, "kitchen_tablet_accept_latency_p50_seconds", "", seconds(value));
                    case "LAT_MS_P95" -> add(samples, "kitchen_tablet_accept_latency_p95_seconds", "", seconds(value));
                    case "TABLETS_ONLINE" -> {
                        String[] parts = value.split("/");
                        if (parts.length != 2) {
                            throw new NumberFormatException(value);
                        }
                        String online = whole(parts[0]);
                        String registered = whole(parts[1]);
                        add(samples, "kitchen_tablets_online", "", online);
                        add(samples, "kitchen_tablets", "", registered);
                    }
                    case "UPTIME_S" -> add(samples, "kitchen_tablet_gateway_uptime_seconds", "", whole(value));
                    case "LAST_SYNC" -> add(samples, "kitchen_tablet_last_sync_timestamp_seconds", "",
                            Long.toString(LocalDateTime.parse(value, SYNC_TIME).atZone(IST).toEpochSecond()));
                    default -> unparsed++;
                }
            } catch (NumberFormatException | DateTimeParseException e) {
                unparsed++;
            }
        }
        add(samples, "kitchen_tablet_gateway_up", "", "1");
        add(samples, "kitchen_tablet_adapter_unparsed_lines", "", Integer.toString(unparsed));
        return render(samples);
    }

    /** What /metrics says when the gateway can't be reached: still a valid page, with up = 0. */
    public String unreachable() {
        Map<String, StringBuilder> samples = new LinkedHashMap<>();
        samples.put("kitchen_tablet_gateway_up", new StringBuilder("kitchen_tablet_gateway_up 0\n"));
        return render(samples);
    }

    private static void add(Map<String, StringBuilder> samples, String name, String labels, String value) {
        samples.get(name).append(name).append(labels).append(' ').append(value).append('\n');
    }

    private static String render(Map<String, StringBuilder> samples) {
        StringBuilder out = new StringBuilder();
        samples.forEach((name, lines) -> {
            if (!lines.isEmpty()) {
                Metric metric = METRICS.get(name);
                out.append("# HELP ").append(name).append(' ').append(metric.help()).append('\n');
                out.append("# TYPE ").append(name).append(' ').append(metric.type()).append('\n');
                out.append(lines);
            }
        });
        return out.toString();
    }

    private static String whole(String value) {
        return Long.toString(Long.parseLong(value.strip()));
    }

    private static String seconds(String millis) {
        return java.math.BigDecimal.valueOf(Long.parseLong(millis.strip()), 3).stripTrailingZeros().toPlainString();
    }
}
