package dev.foodfleet.ch27.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.json.JsonMapper;

/** Jackson 3 as a consumer: what an additive change from the provider does to it. */
class TolerantReaderTest {

    /** A v1.5 response: a field this consumer has never heard of, and a new status. */
    static final String NEWER_RESPONSE = """
            {"id":"o-9001","status":"READY_FOR_PICKUP","etaMinutes":12,"promisedBy":"2026-10-06T13:40:00Z",
             "restaurant":{"id":"r-11","name":"Meghana Foods","cuisine":"Andhra"}}""";

    @Test
    void unknownFieldsAreIgnoredByDefaultButUnknownEnumValuesFail() {
        JsonMapper defaults = JsonMapper.builder().build();

        assertThat(defaults.readValue("{\"id\":\"o-1\",\"status\":\"PLACED\",\"promisedBy\":\"x\"}", OrderSummary.class)
                .status()).isEqualTo(OrderSummary.Status.PLACED);                 // unknown field: fine
        assertThatThrownBy(() -> defaults.readValue(NEWER_RESPONSE, OrderSummary.class))
                .isInstanceOf(InvalidFormatException.class);                        // unknown enum value: fails
    }

    @Test
    void aTolerantReaderMapsNewValuesToUnknown() {
        JsonMapper tolerant = JsonMapper.builder()
                .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE).build();

        OrderSummary order = tolerant.readValue(NEWER_RESPONSE, OrderSummary.class);

        assertThat(order.status()).isEqualTo(OrderSummary.Status.UNKNOWN);
        assertThat(order.status().finished()).isFalse();
        assertThat(order.etaMinutes()).isEqualTo(12);
    }
}
