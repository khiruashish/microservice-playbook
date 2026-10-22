package dev.foodfleet.ch10.bff.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch10.bff.client.Downstream.Eta;
import dev.foodfleet.ch10.bff.client.Downstream.OrderSummary;
import dev.foodfleet.ch10.bff.client.Downstream.Restaurant;
import dev.foodfleet.ch10.bff.client.Downstream.Rider;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrackingCardAssemblerTest {

    private static final Instant EVENING = Instant.parse("2026-10-01T14:48:00Z");   // 8:18 pm in Bengaluru
    private final TrackingCardAssembler assembler = new TrackingCardAssembler();

    private final Restaurant biryaniHouse = new Restaurant("rest-biryani-house", "Biryani House", "Koramangala");
    private final Rider ravi = new Rider("rider-7", "Ravi", "Scooter KA-01-AB-1234");
    private final Eta eta = new Eta(new Eta.Window(6, 12), EVENING.plusSeconds(9 * 60), false);

    private OrderSummary order(String status) {
        return new OrderSummary("o-1", "rest-biryani-house", status, 3, 87_185);
    }

    @Test
    void onTheWayWithEverythingLoaded() {
        TrackingCard card = assembler.assemble(order("PICKED_UP"), Optional.of(eta), Optional.of(biryaniHouse),
                Optional.of(ravi), List.of(), EVENING);

        assertThat(card.title()).isEqualTo("On the way");
        assertThat(card.subtitle()).isEqualTo("from Biryani House, Koramangala");
        assertThat(card.etaText()).isEqualTo("Arriving 8:24–8:30 pm");
        assertThat(card.etaApproximate()).isFalse();
        assertThat(card.rider()).isEqualTo(new TrackingCard.RiderLine("Ravi", "Scooter KA-01-AB-1234"));
        assertThat(card.total()).isEqualTo("₹871.85");
        assertThat(card.actions()).containsExactly("CALL_RIDER", "HELP");
        assertThat(card.degraded()).isEmpty();
    }

    @Test
    void failedPartsAreLeftOutAndNamed() {
        TrackingCard card = assembler.assemble(order("PREPARING"), Optional.empty(), Optional.empty(),
                Optional.empty(), List.of("eta", "restaurant"), EVENING);

        assertThat(card.title()).isEqualTo("Your food is being prepared");
        assertThat(card.etaText()).isNull();
        assertThat(card.subtitle()).isNull();
        assertThat(card.degraded()).containsExactly("eta", "restaurant");
        assertThat(card.actions()).containsExactly("HELP");
    }

    @Test
    void placedOrdersCanBeCancelledAndFinishedOnesShowNoEta() {
        assertThat(assembler.assemble(order("PLACED"), Optional.of(eta), Optional.empty(), Optional.empty(),
                List.of(), EVENING).actions()).containsExactly("CANCEL", "HELP");

        TrackingCard delivered = assembler.assemble(order("DELIVERED"), Optional.of(eta), Optional.of(biryaniHouse),
                Optional.of(ravi), List.of(), EVENING);
        assertThat(delivered.etaText()).isNull();
        assertThat(delivered.rider()).isNull();
    }

    @Test
    void unknownStatusesDoNotBreakOldScreens() {
        assertThat(TrackingCardAssembler.title("AWAITING_DRONE")).isEqualTo("Order update");
    }

    @Test
    void windowsCrossingNoonShowBothSuffixes() {
        Instant beforeNoon = Instant.parse("2026-10-01T06:20:00Z");                     // 11:50 am IST
        assertThat(TrackingCardAssembler.window(beforeNoon, 5, 15)).isEqualTo("11:55 am–12:05 pm");
    }

    @Test
    void rupeesUseIndianGrouping() {
        assertThat(TrackingCardAssembler.rupees(87_185)).isEqualTo("₹871.85");
        assertThat(TrackingCardAssembler.rupees(100_000)).isEqualTo("₹1,000.00");
        assertThat(TrackingCardAssembler.rupees(12_345_650)).isEqualTo("₹1,23,456.50");
        assertThat(TrackingCardAssembler.rupees(1_234_567_890)).isEqualTo("₹1,23,45,678.90");
    }
}
