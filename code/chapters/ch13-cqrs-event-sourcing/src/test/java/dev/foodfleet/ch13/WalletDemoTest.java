package dev.foodfleet.ch13;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WalletDemoTest {

    @Test
    void theEveningAddsUp() {
        assertThat(WalletDemo.run()).contains(
                "Events in stream: 18",
                "Balance ₹585.00, available ₹85.00, payout pending po-1",
                "reads 8 events, not 18",
                "As of 9:00 pm IST the balance was ₹400.00",
                "from a v1 event in rupees: ₹38.50",
                "12 deliveries, earnings ₹540.00, tips ₹60.00",
                "read model still says 1 pending (lag 1), after catch-up 0",
                "same totals: true");
    }
}
