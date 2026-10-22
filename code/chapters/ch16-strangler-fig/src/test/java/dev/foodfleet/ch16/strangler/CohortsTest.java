package dev.foodfleet.ch16.strangler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CohortsTest {

    @Test
    void aCustomerAlwaysLandsInTheSameBucket() {
        assertThat(Cohorts.bucket("c-4711")).isEqualTo(Cohorts.bucket("c-4711"));
    }

    @Test
    void bucketsAreRoughlyUniform() {
        int[] counts = new int[100];
        for (int i = 0; i < 100_000; i++) {
            counts[Cohorts.bucket("c-" + i)]++;
        }
        for (int count : counts) {
            assertThat(count).isBetween(850, 1_150);                 // 1,000 expected per bucket
        }
    }

    @Test
    void raisingThePercentageKeepsEveryoneAlreadyIn() {
        for (int i = 0; i < 10_000; i++) {
            String customer = "c-" + i;
            if (Cohorts.inRollout(customer, 5)) {
                assertThat(Cohorts.inRollout(customer, 25)).isTrue();
            }
        }
    }
}
