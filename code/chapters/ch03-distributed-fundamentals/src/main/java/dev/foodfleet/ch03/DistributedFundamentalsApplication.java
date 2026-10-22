package dev.foodfleet.ch03;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Runs the refund endpoint (port 8084). The other packages are framework-free and exercised by tests. */
@SpringBootApplication
public class DistributedFundamentalsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistributedFundamentalsApplication.class, args);
    }
}
