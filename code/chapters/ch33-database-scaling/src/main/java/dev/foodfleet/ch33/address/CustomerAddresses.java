package dev.foodfleet.ch33.address;

import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerAddresses {

    private final JdbcClient jdbc;
    private final AddressPhase phase;

    public CustomerAddresses(JdbcClient jdbc, @Value("${app.address-migration.phase:EXPAND}") AddressPhase phase) {
        this.jdbc = jdbc;
        this.phase = phase;
    }

    public AddressPhase phase() {
        return phase;
    }

    public void register(long customerId, String name, String address) {
        jdbc.sql(phase.insertSql()).param("id", customerId).param("name", name).param("address", address).update();
    }

    public void change(long customerId, String address) {
        if (jdbc.sql(phase.updateSql()).param("address", address).param("id", customerId).update() == 0) {
            throw new NoSuchElementException("no customer " + customerId);
        }
    }

    public String find(long customerId) {
        return jdbc.sql(phase.selectSql()).param("id", customerId).query(String.class).optional()
                .orElseThrow(() -> new NoSuchElementException("no customer " + customerId));
    }
}
