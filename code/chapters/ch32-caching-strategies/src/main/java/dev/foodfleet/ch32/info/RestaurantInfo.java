package dev.foodfleet.ch32.info;

import java.io.Serializable;
import java.time.LocalTime;

/** Cached with Spring Boot's default JDK serialization, hence Serializable. */
public record RestaurantInfo(long id, String name, LocalTime opens, LocalTime closes) implements Serializable {
}
