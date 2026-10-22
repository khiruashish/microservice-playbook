package dev.foodfleet.ch52.health;

import java.util.Map;

/** One contributor to health, like a Spring Boot {@code HealthIndicator}: a status and optional details. */
@FunctionalInterface
public interface HealthCheck {

    Result check() throws Exception;

    record Result(Status status, Map<String, Object> details) {

        public static Result up() {
            return new Result(Status.UP, Map.of());
        }

        public static Result down(String reason) {
            return new Result(Status.DOWN, Map.of("error", reason));
        }
    }
}
