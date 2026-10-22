package dev.foodfleet.ch31.profiles;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IfMatchTest {

    @Test
    void acceptsStrongAndWeakEntityTags() {
        assertThat(ProfileController.parseVersion("\"7\"")).isEqualTo(7);
        assertThat(ProfileController.parseVersion("W/\"12\"")).isEqualTo(12);
        assertThat(ProfileController.parseVersion(" 3 ")).isEqualTo(3);
    }
}
