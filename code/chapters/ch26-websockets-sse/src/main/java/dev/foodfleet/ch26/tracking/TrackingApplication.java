package dev.foodfleet.ch26.tracking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Live tracking: riders' apps stream positions in over a WebSocket; customers' apps receive them
 * over Server-Sent Events. One instance; the fan-out across instances is discussed in the chapter.
 */
@SpringBootApplication
@EnableScheduling
public class TrackingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackingApplication.class, args);
    }
}
