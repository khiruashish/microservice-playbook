package dev.foodfleet.ch36.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void parsesClaimsAndWritesThemBack() {
        Map<String, Object> m = Json.object("{\"sub\":\"c01\",\"exp\":1744624800,\"aud\":[\"a\",\"b\"],\"x\":null}");
        assertThat(m.get("exp")).isEqualTo(1744624800L);
        assertThat(m.get("aud")).isEqualTo(List.of("a", "b"));
        assertThat(Json.object(Json.write(m))).isEqualTo(m);
    }

    @Test
    void ambiguousOrTrailingInputIsRefused() {
        assertThatThrownBy(() -> Json.object("{\"sub\":\"a\",\"sub\":\"b\"}")).hasMessageContaining("duplicate key");
        assertThatThrownBy(() -> Json.object("{\"sub\":\"a\"} trailing")).hasMessageContaining("trailing");
        assertThatThrownBy(() -> Json.object("[1]")).hasMessageContaining("not a JSON object");
        assertThatThrownBy(() -> Json.parse("[".repeat(40) + "]".repeat(40))).hasMessageContaining("deeply");
    }
}
