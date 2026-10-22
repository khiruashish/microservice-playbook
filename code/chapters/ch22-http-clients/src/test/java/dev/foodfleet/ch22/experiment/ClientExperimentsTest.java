package dev.foodfleet.ch22.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The chapter's measurements, scaled down, as assertions about their direction. Real sockets. */
class ClientExperimentsTest {

    @Test
    void aSharedClientIsFasterThanANewClientPerCallOverTls() throws Exception {
        try (LocalServer https = LocalServer.https()) {
            ClientExperiments.reuse(https, 20, true);
            ClientExperiments.reuse(https, 5, false);
            var shared = ClientExperiments.reuse(https, 60, true);
            var fresh = ClientExperiments.reuse(https, 60, false);

            assertThat(fresh.p50Ms()).isGreaterThan(shared.p50Ms());
        }
    }

    @Test
    void aRequestTimeoutBoundsTheWait() throws Exception {
        try (LocalServer http = LocalServer.http()) {
            assertThat(ClientExperiments.hang(http, Duration.ofMillis(300), Duration.ofSeconds(3)))
                    .startsWith("HttpTimeoutException");
            assertThat(ClientExperiments.hang(http, null, Duration.ofSeconds(1))).startsWith("still blocked");
        }
    }

    @Test
    void virtualThreadsAreNotLimitedByAPoolSize() throws Exception {
        try (LocalServer http = LocalServer.http()) {
            double pool = ClientExperiments.concurrency(http, ClientExperiments.Style.PLATFORM_POOL_50, 300);
            double virtual = ClientExperiments.concurrency(http, ClientExperiments.Style.VIRTUAL_THREADS, 300);

            assertThat(pool).isGreaterThan(1_000.0);           // 300 / 50 x 200 ms = 1.2 s at best
            assertThat(virtual).isLessThan(pool / 2);
        }
    }
}
