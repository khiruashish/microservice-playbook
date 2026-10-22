package dev.foodfleet.ch04.zones.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch04.zones.admin.ZoneCsvParser.InvalidZoneFileException;
import dev.foodfleet.ch04.zones.zone.Zone;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZoneCsvParserTest {

    private final ZoneCsvParser parser = new ZoneCsvParser();

    @Test
    void parsesZonesWithOptionalFee() {
        List<Zone> zones = parser.parse(List.of(
                "pincode,areaName,serviceable,deliveryFeePaise",
                "560001,MG Road,true,2500",
                "",
                "# comment lines are ignored",
                "560076,Bannerghatta Road,yes,",
                "562157,Devanahalli,false,"));

        assertThat(zones).containsExactly(
                new Zone("560001", "MG Road", true, 2_500L),
                new Zone("560076", "Bannerghatta Road", true, null),
                new Zone("562157", "Devanahalli", false, null));
    }

    @Test
    void rejectsTheWholeFileAndReportsEveryProblem() {
        assertThatThrownBy(() -> parser.parse(List.of(
                "pincode,areaName,serviceable,deliveryFeePaise",
                "560001,MG Road,true,2500",
                "56001,Too Short,true,2500",
                "560034,Koramangala,maybe,2900",
                "560001,MG Road again,true,2500",
                "560066,Whitefield,true")))
                .isInstanceOfSatisfying(InvalidZoneFileException.class, e -> assertThat(e.errors()).containsExactly(
                        "line 3: Invalid pincode: 56001",
                        "line 4: serviceable must be true/false, was 'maybe'",
                        "line 5: duplicate pincode 560001",
                        "line 6: expected 4 fields, found 3"));
    }
}
