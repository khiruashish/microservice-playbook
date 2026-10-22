package dev.foodfleet.ch27.api;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for the tests Spring Cloud Contract generates from src/test/resources/contracts:
 * each consumer's expectations become a test of this provider, run in every build.
 */
public abstract class ContractVerifierBase {

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.standaloneSetup(new OrderController());
    }
}
