package dev.foodfleet.ch29.riders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PositionHistoryJsonTest {

    @Test
    void aBatchIsOneJsonArrayWithDotDecimalsWhateverTheLocale() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);       // would print 12,971600 with the default locale
        try {
            String json = PositionHistory.toJson(List.of(
                    new Ping(7, 12.9716, 77.5946, 18.5f, Instant.parse("2026-10-07T08:15:30Z")),
                    new Ping(8, 13.0, 77.6, 0f, Instant.parse("2026-10-07T08:15:31Z"))));

            assertThat(json).isEqualTo("[{\"rider_id\":7,\"recorded_at\":\"2026-10-07T08:15:30Z\",\"lat\":12.971600,"
                    + "\"lon\":77.594600,\"speed_kmh\":18.5},{\"rider_id\":8,\"recorded_at\":\"2026-10-07T08:15:31Z\","
                    + "\"lat\":13.000000,\"lon\":77.600000,\"speed_kmh\":0.0}]");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void impossiblePositionsAreRejected() {
        assertThatThrownBy(() -> new Ping(1, 95, 77, 0, Instant.now())).isInstanceOf(IllegalArgumentException.class);
    }
}
