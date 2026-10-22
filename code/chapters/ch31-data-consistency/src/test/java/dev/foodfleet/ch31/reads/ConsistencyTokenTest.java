package dev.foodfleet.ch31.reads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ConsistencyTokenTest {

    @Test
    void parsesAndPrintsPostgresWalPositions() {
        ConsistencyToken t = ConsistencyToken.parse("16/B374D848");
        assertThat(t.position()).isEqualTo((0x16L << 32) | 0xB374D848L);
        assertThat(t.toString()).isEqualTo("16/B374D848");
        assertThat(ConsistencyToken.parse("0/0").toString()).isEqualTo("0/0");
    }

    @Test
    void comparesAcrossTheHalves() {
        ConsistencyToken lowHalfLarge = ConsistencyToken.parse("1/FFFFFFFF");
        ConsistencyToken nextSegment = ConsistencyToken.parse("2/0");
        assertThat(nextSegment.isAtLeast(lowHalfLarge)).isTrue();
        assertThat(lowHalfLarge.isAtLeast(nextSegment)).isFalse();
        assertThat(lowHalfLarge.newest(nextSegment)).isEqualTo(nextSegment);
        assertThat(nextSegment.isAtLeast(nextSegment)).isTrue();
    }

    @Test
    void comparesUnsignedAtTheTopOfTheRange() {
        assertThat(ConsistencyToken.parse("FFFFFFFF/0").isAtLeast(ConsistencyToken.parse("7FFFFFFF/FFFFFFFF"))).isTrue();
    }

    @Test
    void rejectsWhatIsNotAPosition() {
        assertThatThrownBy(() -> ConsistencyToken.parse("16B374D848")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConsistencyToken.parse("1/")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConsistencyToken.parse("100000000/0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConsistencyToken.parse("x/1")).isInstanceOf(IllegalArgumentException.class);
    }
}
