package dev.foodfleet.ch04.zones.zone;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * IV. Backing services: PostgreSQL is an attached resource located by
 * {@code SPRING_DATASOURCE_URL}. Pointing the same build at a local container, a managed
 * cloud database or a read replica is a configuration change, never a code change.
 */
@Repository
public class JdbcZoneStore implements ZoneStore {

    private final JdbcClient jdbc;

    public JdbcZoneStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Zone> find(String pincode) {
        return jdbc.sql("""
                        select pincode, area_name, serviceable, delivery_fee_paise
                        from zone where pincode = :pincode
                        """)
                .param("pincode", pincode)
                .query((rs, rowNum) -> new Zone(
                        rs.getString("pincode"),
                        rs.getString("area_name"),
                        rs.getBoolean("serviceable"),
                        rs.getObject("delivery_fee_paise", Long.class)))
                .optional();
    }

    /** Idempotent: re-running an import with the same file leaves the same data (chapter 3). */
    @Override
    @Transactional
    public int upsertAll(List<Zone> zones) {
        int written = 0;
        for (Zone zone : zones) {
            written += jdbc.sql("""
                            insert into zone (pincode, area_name, serviceable, delivery_fee_paise)
                            values (:pincode, :areaName, :serviceable, :fee)
                            on conflict (pincode) do update
                               set area_name = excluded.area_name,
                                   serviceable = excluded.serviceable,
                                   delivery_fee_paise = excluded.delivery_fee_paise
                            """)
                    .param("pincode", zone.pincode())
                    .param("areaName", zone.areaName())
                    .param("serviceable", zone.serviceable())
                    .param("fee", zone.deliveryFeePaise())
                    .update();
        }
        return written;
    }

    @Override
    public long count() {
        return jdbc.sql("select count(*) from zone").query(Long.class).single();
    }
}
