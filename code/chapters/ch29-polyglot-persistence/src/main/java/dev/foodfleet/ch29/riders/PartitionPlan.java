package dev.foodfleet.ch29.riders;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which daily partitions of rider_pings to create and which to drop. Dropping a day's partition took
 * 217-222 ms and 82-83 kB of WAL in the chapter's measurement; deleting the same 1.44 M rows took
 * 1,061-1,152 ms, wrote 171 MB of WAL and gave no space back until vacuum.
 */
public final class PartitionPlan {

    static final String PARENT = "rider_pings";
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern NAME = Pattern.compile(PARENT + "_(\\d{8})");

    public record Partition(String name, LocalDate day) {

        /** The DDL to create it; days are UTC. */
        public String createSql() {
            return "CREATE TABLE IF NOT EXISTS " + name + " PARTITION OF " + PARENT
                    + " FOR VALUES FROM ('" + day + " 00:00:00+00') TO ('" + day.plusDays(1) + " 00:00:00+00')";
        }

        public String dropSql() {
            return "DROP TABLE IF EXISTS " + name;
        }
    }

    public record Plan(List<Partition> create, List<Partition> drop) {
    }

    private PartitionPlan() {
    }

    public static Partition partitionFor(LocalDate day) {
        return new Partition(PARENT + "_" + day.format(DAY), day);
    }

    /**
     * Keeps {@code retentionDays} days up to and including today, and has {@code daysAhead} future days
     * ready so that a write just after midnight never finds no partition.
     */
    public static Plan plan(LocalDate today, int retentionDays, int daysAhead, Collection<String> existing) {
        if (retentionDays < 1 || daysAhead < 1) {
            throw new IllegalArgumentException("retentionDays and daysAhead must be at least 1");
        }
        List<Partition> create = new ArrayList<>();
        for (LocalDate d = today.minusDays(retentionDays - 1); !d.isAfter(today.plusDays(daysAhead)); d = d.plusDays(1)) {
            Partition p = partitionFor(d);
            if (!existing.contains(p.name())) {
                create.add(p);
            }
        }
        LocalDate oldestKept = today.minusDays(retentionDays - 1);
        List<Partition> drop = new ArrayList<>();
        for (String name : existing) {
            Matcher m = NAME.matcher(name);
            if (m.matches()) {
                LocalDate day = LocalDate.parse(m.group(1), DAY);
                if (day.isBefore(oldestKept)) {
                    drop.add(new Partition(name, day));
                }
            }
        }
        drop.sort((a, b) -> a.day().compareTo(b.day()));
        return new Plan(List.copyOf(create), List.copyOf(drop));
    }
}
