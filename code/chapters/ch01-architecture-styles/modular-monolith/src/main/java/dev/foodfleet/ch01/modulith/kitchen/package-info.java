/**
 * Kitchen module: owns tickets and the {@code kitchen} schema.
 * May only depend on the order module's public API (its events and its availability port).
 */
@ApplicationModule(displayName = "Kitchen", allowedDependencies = "order")
package dev.foodfleet.ch01.modulith.kitchen;

import org.springframework.modulith.ApplicationModule;
