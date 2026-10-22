package dev.foodfleet.ch04.zones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * FoodFleet zone-service.
 *
 * <p>XII. Admin processes: the same image runs either as the long-lived web service or as a
 * one-off task, for example
 * {@code java -jar app.jar --foodfleet.admin.task=import-zones --foodfleet.admin.file=/data/zones.csv}.
 * In task mode the process exits when the task finishes, with exit code 0 on success. A failure
 * propagates as an exception and the JVM exits non-zero, so a Kubernetes Job sees the outcome.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ZoneServiceApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ZoneServiceApplication.class, args);
        if (context.getEnvironment().containsProperty("foodfleet.admin.task")) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
