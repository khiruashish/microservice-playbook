package dev.foodfleet.ch08.eta.api;

import dev.foodfleet.ch08.eta.domain.EtaWindow;
import org.springframework.boot.jackson.JacksonComponent;
import org.springframework.boot.jackson.ObjectValueSerializer;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;

/**
 * Jackson 3 serializer, registered automatically because of {@code @JacksonComponent}
 * (Boot 3's {@code @JsonComponent}). The domain record stays free of JSON annotations.
 *
 * <pre>{"from": 24, "to": 30, "display": "24–30 min"}</pre>
 */
@JacksonComponent
public class EtaWindowJacksonComponent {

    public static class Serializer extends ObjectValueSerializer<EtaWindow> {

        @Override
        protected void serializeObject(EtaWindow window, JsonGenerator json, SerializationContext context) {
            json.writeNumberProperty("from", window.fromMinutes());
            json.writeNumberProperty("to", window.toMinutes());
            json.writeStringProperty("display", window.display());
        }
    }
}
