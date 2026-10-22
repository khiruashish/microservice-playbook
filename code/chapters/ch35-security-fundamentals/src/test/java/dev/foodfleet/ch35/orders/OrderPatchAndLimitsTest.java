package dev.foodfleet.ch35.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrderPatchAndLimitsTest {

    @Test
    void notesAreBoundedAndPhonesValidated() {
        assertThatThrownBy(() -> new OrderPatch("x".repeat(201), null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> new OrderPatch("line\u0000break", null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> new OrderPatch(null, "call me")).isInstanceOf(BadRequestException.class);
        assertThat(new OrderPatch("Gate 2", "+919812345678").contactPhone()).isEqualTo("+919812345678");
    }

    @Test
    void pageSizesAreBounded() {
        assertThat(PageLimits.DEFAULT.size(null)).isEqualTo(20);
        assertThat(PageLimits.DEFAULT.size(100)).isEqualTo(100);
        assertThatThrownBy(() -> PageLimits.DEFAULT.size(1_000_000)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PageLimits.DEFAULT.size(0)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void deepOffsetsAreRefused() {
        assertThat(PageLimits.DEFAULT.offset(3, 20)).isEqualTo(60);
        assertThatThrownBy(() -> PageLimits.DEFAULT.offset(600, 20)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PageLimits.DEFAULT.offset(-1, 20)).isInstanceOf(BadRequestException.class);
    }
}
