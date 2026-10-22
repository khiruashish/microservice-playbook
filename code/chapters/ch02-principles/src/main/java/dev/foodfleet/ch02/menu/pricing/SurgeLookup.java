package dev.foodfleet.ch02.menu.pricing;

/** Port: fetch the live surge multiplier for a zone. May throw if pricing-service is unavailable. */
@FunctionalInterface
public interface SurgeLookup {

    int multiplierBps(String zone);
}
