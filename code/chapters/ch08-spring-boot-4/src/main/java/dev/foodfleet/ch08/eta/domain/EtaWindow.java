package dev.foodfleet.ch08.eta.domain;

/** "24–30 min": the range promised to the customer. Serialized by {@code EtaWindowJacksonComponent}. */
public record EtaWindow(int fromMinutes, int toMinutes) {

    public EtaWindow {
        if (fromMinutes < 1 || toMinutes < fromMinutes) {
            throw new IllegalArgumentException("Invalid window " + fromMinutes + "–" + toMinutes);
        }
    }

    public String display() {
        return fromMinutes + "–" + toMinutes + " min";
    }
}
