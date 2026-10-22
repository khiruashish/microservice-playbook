package dev.foodfleet.ch17.logship;

import java.io.IOException;
import java.util.List;

/** Where shipped lines go: a log collector's HTTP endpoint, a Kafka topic, stdout. */
@FunctionalInterface
public interface Sink {

    /** Must either accept the whole batch or throw; the shipper then retries the same lines. */
    void send(List<String> lines) throws IOException;
}
