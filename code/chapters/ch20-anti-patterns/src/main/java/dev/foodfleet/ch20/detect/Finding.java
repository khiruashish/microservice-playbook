package dev.foodfleet.ch20.detect;

/**
 * One anti-pattern instance: what, where, the evidence from the model, and the usual remedy.
 * {@code key} identifies it stably across runs, for baselines ("we know about this one").
 */
public record Finding(AntiPattern pattern, Severity severity, String subject, String evidence, String remedy) {

    public enum Severity { HIGH, MEDIUM, LOW }

    public enum AntiPattern {
        DISTRIBUTED_MONOLITH("Distributed monolith"),
        SYNC_CHAIN("Deep synchronous call chain"),
        CYCLIC_DEPENDENCY("Cyclic service dependency"),
        CHATTY("Chatty communication (N+1)"),
        FAN_OUT("Wide synchronous fan-out"),
        NANO_SERVICE("Nano-service"),
        SHARED_DATABASE("Shared database"),
        SHARED_DOMAIN_LIBRARY("Shared domain library"),
        LIBRARY_VERSION_SKEW("Shared-library version skew");

        public final String title;

        AntiPattern(String title) {
            this.title = title;
        }
    }

    public String key() {
        return pattern + ":" + subject;
    }
}
