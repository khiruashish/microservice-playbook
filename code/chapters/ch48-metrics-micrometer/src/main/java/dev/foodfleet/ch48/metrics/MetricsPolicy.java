package dev.foodfleet.ch48.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

import java.time.Duration;
import java.util.List;

/**
 * FoodFleet's rules for every service's meters, applied as {@link MeterFilter}s before any meter is registered:
 * <ul>
 *   <li>common tags that every series needs and nothing else (the region; service and pod come from the scrape);</li>
 *   <li>latency histograms that can be aggregated across pods: a percentile histogram bounded to the range that
 *       matters, plus explicit SLO boundaries, instead of client-side percentiles that can't be combined;</li>
 *   <li>a cap on the values one tag may take, so a raw path or an ID can't create unbounded series;</li>
 *   <li>tags that must never be metric tags (they belong on spans and wide events) removed.</li>
 * </ul>
 */
public final class MetricsPolicy {

    private MetricsPolicy() {
    }

    /** The latency boundaries FoodFleet's SLOs are written against (chapter 51). */
    public static final List<Duration> HTTP_SLOS = List.of(Duration.ofMillis(100), Duration.ofMillis(250),
            Duration.ofMillis(500), Duration.ofSeconds(1));

    /** Identifiers that are welcome on spans and events and never on meters. */
    public static final List<String> NEVER_TAGS = List.of("order.id", "user.id", "restaurant.id", "rider.id", "http.url");

    public static List<MeterFilter> foodFleet(String region) {
        return List.of(
                MeterFilter.commonTags(Tags.of("region", region)),
                MeterFilter.ignoreTags(NEVER_TAGS.toArray(String[]::new)),
                MeterFilter.maximumAllowableTags("http.server.requests", "uri", 200, MeterFilter.deny()),
                boundedLatencyHistograms("http.server.requests", "http.client.requests", "foodfleet."));
    }

    /**
     * Turns on percentile histograms for timers whose names start with one of {@code prefixes}, limited to 1 ms – 10 s
     * (Micrometer's default range for timers is 1 ms – 30 s), with the SLO boundaries added as exact buckets.
     */
    public static MeterFilter boundedLatencyHistograms(String... prefixes) {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getType() != Meter.Type.TIMER || !matches(id.getName(), prefixes)) {
                    return config;
                }
                return DistributionStatisticConfig.builder()
                        .percentilesHistogram(true)
                        .minimumExpectedValue((double) Duration.ofMillis(1).toNanos())
                        .maximumExpectedValue((double) Duration.ofSeconds(10).toNanos())
                        .serviceLevelObjectives(HTTP_SLOS.stream().mapToDouble(d -> (double) d.toNanos()).toArray())
                        .build()
                        .merge(config);
            }
        };
    }

    private static boolean matches(String name, String... prefixes) {
        for (String p : prefixes) {
            if (name.startsWith(p)) {
                return true;
            }
        }
        return false;
    }
}
