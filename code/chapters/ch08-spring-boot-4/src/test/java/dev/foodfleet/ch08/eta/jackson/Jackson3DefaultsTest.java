package dev.foodfleet.ch08.eta.jackson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

/**
 * What changes when a Boot 3 service moves to Jackson 3, shown on a plain {@code JsonMapper}
 * (Jackson's own defaults, before Spring Boot's configuration is applied).
 */
class Jackson3DefaultsTest {

    record Prep(String orderId, int minutesRemaining) {
    }

    record Arrival(Instant at) {
    }

    record Rider(Optional<String> name) {
    }

    private final JsonMapper mapper = JsonMapper.builder().build();   // immutable; change it with rebuild()

    @Test
    void javaTimeWorksOutOfTheBoxAndDatesAreIsoStrings() {
        // Jackson 2 needed the jackson-datatype-jsr310 module, and then wrote a number by default
        assertThat(mapper.writeValueAsString(new Arrival(Instant.parse("2026-10-01T08:27:00Z"))))
                .isEqualTo("{\"at\":\"2026-10-01T08:27:00Z\"}");
    }

    @Test
    void optionalIsBuiltInToo() {
        assertThat(mapper.writeValueAsString(new Rider(Optional.of("Ravi")))).isEqualTo("{\"name\":\"Ravi\"}");
    }

    @Test
    void nullForAPrimitiveIsNowAnError() {
        // Jackson 2 silently turned null into 0: a 0-minute prep time would have reached customers
        assertThatThrownBy(() -> mapper.readValue("{\"orderId\":\"o-1\",\"minutesRemaining\":null}", Prep.class))
                .isInstanceOf(DatabindException.class);
    }

    @Test
    void exceptionsAreUncheckedNow() {
        // No 'throws JsonProcessingException' anywhere: JacksonException extends RuntimeException
        assertThat(RuntimeException.class).isAssignableFrom(JacksonException.class);
        assertThatThrownBy(() -> mapper.readValue("{not json", Prep.class)).isInstanceOf(JacksonException.class);
    }
}
