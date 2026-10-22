package dev.foodfleet.ch04.zones.admin;

import dev.foodfleet.ch04.zones.zone.Zone;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses the zone import file: {@code pincode,areaName,serviceable,deliveryFeePaise}, with a header
 * row. An empty fee means "use the configured default".
 *
 * <p>All-or-nothing: every problem in the file is collected and reported together, and nothing is
 * imported if any line is bad. A half-applied admin task leaves production in a state nobody
 * designed.
 */
public final class ZoneCsvParser {

    public List<Zone> parse(List<String> lines) {
        List<Zone> zones = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int i = 1; i < lines.size(); i++) { // line 0 is the header
            String line = lines.get(i).strip();
            int lineNo = i + 1;
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split(",", -1);
            if (f.length != 4) {
                errors.add("line " + lineNo + ": expected 4 fields, found " + f.length);
                continue;
            }
            try {
                String pincode = f[0].strip();
                boolean serviceable = parseBoolean(f[2].strip());
                Long fee = f[3].isBlank() ? null : Long.parseLong(f[3].strip());
                Zone zone = new Zone(pincode, f[1].strip(), serviceable, fee);
                if (!seen.add(pincode)) {
                    errors.add("line " + lineNo + ": duplicate pincode " + pincode);
                    continue;
                }
                zones.add(zone);
            } catch (IllegalArgumentException e) { // includes NumberFormatException
                errors.add("line " + lineNo + ": " + e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidZoneFileException(errors);
        }
        return zones;
    }

    private static boolean parseBoolean(String value) {
        return switch (value.toLowerCase()) {
            case "true", "yes", "y" -> true;
            case "false", "no", "n" -> false;
            default -> throw new IllegalArgumentException("serviceable must be true/false, was '" + value + "'");
        };
    }

    public static final class InvalidZoneFileException extends RuntimeException {

        private final transient List<String> errors;

        InvalidZoneFileException(List<String> errors) {
            super("Zone file rejected, " + errors.size() + " problem(s): " + String.join("; ", errors));
            this.errors = List.copyOf(errors);
        }

        public List<String> errors() {
            return errors;
        }
    }
}
