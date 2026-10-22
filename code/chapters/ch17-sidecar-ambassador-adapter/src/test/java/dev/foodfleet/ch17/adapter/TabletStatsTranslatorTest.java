package dev.foodfleet.ch17.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch17.fake.FakeTabletGateway;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

class TabletStatsTranslatorTest {

    static final String STATS = """
            # KDS-GW v2.3 stats
            UPTIME_S=86400
            ORD_ACC=1532
            ORD_REJ=47
            LAT_MS_P95=820
            LAT_MS_P50=210
            TABLETS_ONLINE=37/40
            LAST_SYNC=06-10-2026 12:41
            """;

    private final TabletStatsTranslator translator = new TabletStatsTranslator();

    @Test
    void translatesNamesUnitsAndLabels() {
        String metrics = translator.translate(STATS);

        assertThat(metrics).contains(
                "# TYPE kitchen_tablet_orders_total counter",
                "kitchen_tablet_orders_total{outcome=\"accepted\"} 1532",
                "kitchen_tablet_orders_total{outcome=\"rejected\"} 47",
                "kitchen_tablet_accept_latency_p95_seconds 0.82",
                "kitchen_tablet_accept_latency_p50_seconds 0.21",
                "kitchen_tablets_online 37",
                "kitchen_tablets 40",
                "kitchen_tablet_gateway_uptime_seconds 86400",
                "kitchen_tablet_last_sync_timestamp_seconds 1791270660",     // 12:41 IST = 07:11 UTC
                "kitchen_tablet_gateway_up 1",
                "kitchen_tablet_adapter_unparsed_lines 0");
    }

    @Test
    void newFirmwareKeysAndGarbageAreCountedNotFatal() {
        String metrics = translator.translate(STATS + "QUEUE_DEPTH=4\nTABLETS_ONLINE=37of40\n!!!\n");

        assertThat(metrics).contains("kitchen_tablet_adapter_unparsed_lines 3", "kitchen_tablet_orders_total{outcome=\"accepted\"} 1532");
    }

    @Test
    void anUnreachableGatewayIsStillAValidPage() {
        assertThat(translator.unreachable()).contains("# TYPE kitchen_tablet_gateway_up gauge", "kitchen_tablet_gateway_up 0");
    }

    @Test
    void theSidecarServesTranslatedMetricsOverHttp() throws Exception {
        try (FakeTabletGateway gateway = new FakeTabletGateway(STATS);
             MetricsAdapter adapter = new MetricsAdapter(0, URI.create(gateway.baseUrl() + "/stats"));
             HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + adapter.port() + "/metrics")).build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("kitchen_tablets_online 37");
        }
    }
}
