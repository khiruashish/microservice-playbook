package dev.foodfleet.ch20.detect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch20.model.ModelParser;
import dev.foodfleet.ch20.model.SystemModel;
import org.junit.jupiter.api.Test;

class ModelParserTest {

    @Test
    void parsesEveryKindOfLine() {
        SystemModel model = ModelParser.parse("""
                system demo   # a comment
                service a team=x endpoints=3 loc=1200
                call a -> b per-request=4 sync
                call a -> c async
                table t owner=a writers=b readers=c,d
                library lib kind=domain a=1.0.0 b=1.1.0
                deploy 2024-01-02T10:00 a,b
                """);

        assertThat(model.name()).isEqualTo("demo");
        assertThat(model.service("a").orElseThrow().loc()).isEqualTo(1200);
        assertThat(model.calls().getFirst()).isEqualTo(new SystemModel.Call("a", "b", 4, true));
        assertThat(model.calls().getLast().sync()).isFalse();
        assertThat(model.tables().getFirst().readers()).hasSize(2);
        assertThat(model.libraries().getFirst().versionByService()).containsKeys("a", "b");
        assertThat(model.deploys().getFirst().services()).hasSize(2);
    }

    @Test
    void errorsNameTheLine() {
        assertThatThrownBy(() -> ModelParser.parse("system x\ncall a b\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line 2");
    }
}
