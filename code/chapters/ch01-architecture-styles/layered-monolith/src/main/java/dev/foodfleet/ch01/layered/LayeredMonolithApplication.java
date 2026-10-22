package dev.foodfleet.ch01.layered;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * L1 — the classic layered monolith: controller → service → repository, one deployable,
 * one process, one shared in-memory "database". Packages are organised by technical layer,
 * so nothing stops the order service from reaching directly into kitchen and notification code.
 */
@SpringBootApplication
public class LayeredMonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(LayeredMonolithApplication.class, args);
    }
}
