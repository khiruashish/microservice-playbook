package dev.foodfleet.ch16.acl;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch16.legacy.LegacyOrderRow;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LegacyOrderTranslatorTest {

    private final LegacyOrderTranslator acl = new LegacyOrderTranslator();

    private Order translated(LegacyOrderRow row) {
        Translation t = acl.translate(row);
        assertThat(t).isInstanceOf(Translation.Translated.class);
        return ((Translation.Translated) t).order();
    }

    @Test
    void aDeliveredGoldOrderBecomesADomainOrder() {
        Order order = translated(new LegacyOrderRow("FF1042 ", "D", 412.35, "G",
                "12B|80 Feet Road|Koramangala|560034", "04-10-2026 21:40"));

        assertThat(order.id()).isEqualTo("FF1042");
        assertThat(order.status()).isEqualTo(Order.Status.DELIVERED);
        assertThat(order.totalPaise()).isEqualTo(41_235L);
        assertThat(order.tier()).isEqualTo(Order.Tier.GOLD);
        assertThat(order.address().pincode()).isEqualTo(Optional.of("560034"));
        assertThat(order.deliveredAt()).isEqualTo(Optional.of(Instant.parse("2026-10-04T16:10:00Z")));   // IST -> UTC
    }

    @Test
    void rupeesBecomeExactPaiseEvenWhenTheDoubleIsSlightlyOff() {
        assertThat(LegacyOrderTranslator.paise(0.1 + 0.2)).isEqualTo(30L);   // 0.30000000000000004
        assertThat(LegacyOrderTranslator.paise(1.005)).isEqualTo(101L);      // decimal string "1.005", half-up
        assertThat(LegacyOrderTranslator.paise(199.99)).isEqualTo(19_999L);
    }

    @Test
    void oldAddressesWithoutPincodeAndOldTierCodesAreAccepted() {
        Order order = translated(new LegacyOrderRow("FF7", "p", 250.0, "N", "4|MG Road|", null));

        assertThat(order.status()).isEqualTo(Order.Status.PLACED);           // lower-case codes exist too
        assertThat(order.tier()).isEqualTo(Order.Tier.NONE);
        assertThat(order.address()).isEqualTo(new Order.Address("4", "MG Road", "", Optional.empty()));
    }

    @Test
    void unknownStatusesAreQuarantinedNotGuessed() {
        assertThat(acl.translate(new LegacyOrderRow("FF8", "Z", 250.0, null, "", null)))
                .isEqualTo(new Translation.Quarantined("FF8", "unknown status 'Z'"));
    }

    @Test
    void deliveredWithoutATimeIsQuarantined() {
        assertThat(acl.translate(new LegacyOrderRow("FF9", "D", 250.0, null, "", " ")))
                .isEqualTo(new Translation.Quarantined("FF9", "delivered without a delivery time"));
    }

    @Test
    void unreadableTimesAndNonPositiveAmountsAreQuarantined() {
        assertThat(acl.translate(new LegacyOrderRow("FF10", "D", 250.0, null, "", "2026-10-04 21:40")))
                .isInstanceOf(Translation.Quarantined.class);
        assertThat(acl.translate(new LegacyOrderRow("FF11", "P", 0.0, null, "", null)))
                .isInstanceOf(Translation.Quarantined.class);
    }
}
