package dev.foodfleet.ch06.decomposition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch06.decomposition.GitLogImporter.Result;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GitLogImporterTest {

    private static final List<String> MAPPING = List.of(
            "# path prefix = module",
            "src/main/java/com/foodfleet/order/=order",
            "src/main/java/com/foodfleet/order/line/=order-line",
            "src/main/java/com/foodfleet/cart/=cart",
            "src/main/java/com/foodfleet/menu/=menu",
            "src/main/resources/db/migration/order/=order");

    private final GitLogImporter importer = new GitLogImporter(GitLogImporter.parseRules(MAPPING), 3);

    @Test
    void longestPrefixWins() {
        assertThat(importer.moduleOf("src/main/java/com/foodfleet/order/line/OrderLine.java")).contains("order-line");
        assertThat(importer.moduleOf("src/main/java/com/foodfleet/order/Order.java")).contains("order");
        assertThat(importer.moduleOf("pom.xml")).isEmpty();
    }

    @Test
    void importsRealGitLogOutput() {
        // git log --no-merges --name-only --pretty=format:'#%h'
        Result result = importer.importLog(List.of(
                "#a1b2c3d",
                "src/main/java/com/foodfleet/order/Order.java",
                "src/main/java/com/foodfleet/order/line/OrderLine.java",
                "src/main/resources/db/migration/order/V7__add_line_note.sql",
                "",
                "#b2c3d4e",
                "README.md",
                "pom.xml",
                "",
                "#c3d4e5f",
                "src/main/java/com/foodfleet/order/Order.java",
                "src/main/java/com/foodfleet/order/line/OrderLine.java",
                "src/main/java/com/foodfleet/cart/Cart.java",
                "src/main/java/com/foodfleet/menu/Menu.java",
                "",
                "#d4e5f6a",
                "src/main/java/com/foodfleet/cart/Cart.java"));

        assertThat(result.commitsSeen()).isEqualTo(4);
        assertThat(result.droppedUnmapped()).as("docs and build files only").isEqualTo(1);
        assertThat(result.droppedNoisy()).as("4 modules > limit of 3: a mass change").isEqualTo(1);
        assertThat(result.commits()).containsExactly(Set.of("order", "order-line"), Set.of("cart"));
    }

    @Test
    void rejectsMalformedMapping() {
        assertThatThrownBy(() -> GitLogImporter.parseRules(List.of("src/main/java/order")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GitLogImporter.parseRules(List.of("# only comments")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
