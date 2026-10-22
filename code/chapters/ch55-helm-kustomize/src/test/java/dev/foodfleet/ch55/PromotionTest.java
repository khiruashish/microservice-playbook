package dev.foodfleet.ch55;

import dev.foodfleet.ch55.promotion.ImageRef;
import dev.foodfleet.ch55.promotion.PromotionPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionTest {

    static final String D1 = "sha256:" + "a".repeat(64);
    static final String D2 = "sha256:" + "b".repeat(64);

    @Test
    void parsesTagsDigestsAndRegistryPorts() {
        ImageRef r = ImageRef.parse("registry.foodfleet.internal:5000/menu-service:1.4.2@" + D1);
        assertThat(r.repository()).isEqualTo("registry.foodfleet.internal:5000/menu-service");
        assertThat(r.tag()).isEqualTo("1.4.2");
        assertThat(r.digest()).isEqualTo(D1);
        assertThat(ImageRef.parse("registry:5000/menu-service").tag() == null).isTrue();
        assertThat(ImageRef.parse("menu-service").pinned()).isFalse();
        assertThat(ImageRef.parse("menu-service:ch54").pinTo(D2).toString()).isEqualTo("menu-service@" + D2);
    }

    @Test
    void rejectsMalformedDigests() {
        boolean thrown = false;
        try {
            ImageRef.parse("menu-service@sha256:123");
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertThat(thrown).isTrue();
    }

    @Test
    void tagsAreAllowedInDevOnly() {
        String yaml = "spec:\n  containers:\n    - name: app\n      image: docker.io/foodfleet/menu-service:ch54\n";
        assertThat(PromotionPolicy.check("dev", yaml).isEmpty()).isTrue();
        assertThat(PromotionPolicy.check("prod", yaml)).containsExactly("prod: docker.io/foodfleet/menu-service:ch54 is not pinned by digest");
        assertThat(PromotionPolicy.check("prod", yaml.replace(":ch54", "@" + D1)).isEmpty()).isTrue();
    }

    @Test
    void productionMustRunWhatStagingTested() {
        String staging = "image: docker.io/foodfleet/menu-service@" + D1 + "\n";
        assertThat(PromotionPolicy.samePromoted("staging", staging, "prod", staging).isEmpty()).isTrue();
        assertThat(PromotionPolicy.samePromoted("staging", staging, "prod", staging.replace(D1, D2)).getFirst())
                .contains("prod: docker.io/foodfleet/menu-service runs " + D2);
    }

    @Test
    void setsTheDigestInAnOverlay() {
        String k = "images:\n  - name: docker.io/foodfleet/menu-service\n    digest: sha256:REPLACE_WITH_PROMOTED_DIGEST\npatches: []\n";
        String out = PromotionPolicy.setDigest(k, "docker.io/foodfleet/menu-service", D1);
        assertThat(out).isEqualTo(k.replace("sha256:REPLACE_WITH_PROMOTED_DIGEST", D1));
    }
}
