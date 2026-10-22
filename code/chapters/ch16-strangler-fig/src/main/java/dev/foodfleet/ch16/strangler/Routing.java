package dev.foodfleet.ch16.strangler;

/**
 * How the facade routes one capability during migration.
 * <ul>
 *   <li>{@code LEGACY}: the monolith only (start, and the rollback position)</li>
 *   <li>{@code SHADOW}: the monolith answers; the new service is called too and compared, its answer discarded</li>
 *   <li>{@code CANARY}: customers in the first {@code percent} buckets get the new service</li>
 *   <li>{@code NEW}: the new service only; the monolith code can be deleted</li>
 * </ul>
 */
public record Routing(Mode mode, int percent, boolean fallbackToLegacy) {

    public enum Mode { LEGACY, SHADOW, CANARY, NEW }

    public static Routing legacy() {
        return new Routing(Mode.LEGACY, 0, false);
    }

    public static Routing shadow() {
        return new Routing(Mode.SHADOW, 0, false);
    }

    public static Routing canary(int percent) {
        return new Routing(Mode.CANARY, percent, false);
    }

    public static Routing newOnly() {
        return new Routing(Mode.NEW, 100, false);
    }

    public Routing withFallback() {
        return new Routing(mode, percent, true);
    }

    public Routing {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("percent must be 0..100");
        }
    }
}
