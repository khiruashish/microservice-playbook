package dev.foodfleet.ch30;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Order placement across two services without a distributed transaction. The orders and payments schemas stand
 * in for two services' databases; in production the relay talks to payment-service over a broker (chapter 25).
 */
@SpringBootApplication
@EnableScheduling
public class DistributedTxApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistributedTxApplication.class, args);
    }
}
