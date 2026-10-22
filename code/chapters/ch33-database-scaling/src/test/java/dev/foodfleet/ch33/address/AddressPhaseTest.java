package dev.foodfleet.ch33.address;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AddressPhaseTest {

    @Test
    void expandWritesBothColumnsAndStillReadsTheOldOne() {
        assertThat(AddressPhase.EXPAND.updateSql())
                .isEqualTo("UPDATE customers SET address = :address, delivery_address = :address WHERE id = :id");
        assertThat(AddressPhase.EXPAND.selectSql()).isEqualTo("SELECT address FROM customers WHERE id = :id");
    }

    @Test
    void readNewSwitchesTheReadButKeepsWritingBoth() {
        assertThat(AddressPhase.READ_NEW.writeColumns()).isEqualTo(List.of("address", "delivery_address"));
        assertThat(AddressPhase.READ_NEW.readColumn()).isEqualTo("delivery_address");
    }

    @Test
    void contractNoLongerTouchesTheOldColumn() {
        assertThat(AddressPhase.CONTRACT.updateSql()).isEqualTo("UPDATE customers SET delivery_address = :address WHERE id = :id");
        assertThat(AddressPhase.CONTRACT.selectSql()).isEqualTo("SELECT delivery_address FROM customers WHERE id = :id");
    }

    @Test
    void insertsFollowTheWriteColumns() {
        assertThat(AddressPhase.EXPAND.insertSql()).isEqualTo(
                "INSERT INTO customers (id, name, address, delivery_address) VALUES (:id, :name, :address, :address)");
        assertThat(AddressPhase.CONTRACT.insertSql()).isEqualTo(
                "INSERT INTO customers (id, name, delivery_address) VALUES (:id, :name, :address)");
    }

    @Test
    void neighbouringPhasesAlwaysShareTheColumnTheyRead() {
        // during a rolling deploy instances in phase N and N+1 run together: what one reads, the other must write
        AddressPhase[] phases = AddressPhase.values();
        for (int i = 0; i + 1 < phases.length; i++) {
            assertThat(phases[i + 1].writeColumns().contains(phases[i].readColumn())).isTrue();
            assertThat(phases[i].writeColumns().contains(phases[i + 1].readColumn())).isTrue();
        }
    }
}
