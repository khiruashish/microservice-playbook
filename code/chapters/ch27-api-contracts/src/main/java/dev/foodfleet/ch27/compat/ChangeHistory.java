package dev.foodfleet.ch27.compat;

import static dev.foodfleet.ch27.compat.Schema.Field;
import static dev.foodfleet.ch27.compat.Schema.Type;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * A year of changes to FoodFleet's Order API ({@code GET /orders/{id}}), applied one after
 * another, and the five consumers that read it. For each change: which consumers actually break
 * (decoding as their client libraries do), what a structural diff reports, and whether
 * consumer-driven contract tests fail.
 */
public final class ChangeHistory {

    public record Change(String description, UnaryOperator<Schema> apply, Map<String, String> renames,
                         List<String> newStatusesSent) {
    }

    public record Verdict(Change change, List<String> brokenConsumers, StructuralDiff.Severity diff,
                          List<String> contractFailures, Map<String, List<String>> problems) {
        public boolean breaksSomeone() {
            return !brokenConsumers.isEmpty();
        }

        public boolean caughtByDiff() {
            return diff == StructuralDiff.Severity.ERROR;
        }

        public boolean caughtByContracts() {
            return !contractFailures.isEmpty();
        }
    }

    static final List<String> STATUSES = List.of("PLACED", "ACCEPTED", "PREPARING", "PICKED_UP", "DELIVERED", "CANCELLED");

    public static Schema v1() {
        return Schema.of(
                "id", Field.string(""),
                "status", Field.enumeration(STATUSES),
                "eta", Field.integer("minutes until delivery"),
                "riderName", Field.string(""),
                "totalPaise", Field.integer(""),
                "placedAt", Field.string("ISO-8601 instant"),
                "legacyCode", Field.string(""),
                "restaurant", Field.object(Schema.of("name", Field.string(""), "area", Field.string(""))),
                // Behaviour, not shape: not in the response schema (diff tools can't see it), but a
                // contract with an "unknown order" interaction tests it.
                "@unknownOrder", Field.string("404 with a problem document"));
    }

    public static List<Change> changes() {
        List<String> withReady = new ArrayList<>(STATUSES);
        withReady.add(3, "READY_FOR_PICKUP");
        List<String> splitCancel = new ArrayList<>(withReady);
        splitCancel.remove("CANCELLED");
        splitCancel.add("CANCELLED_BY_CUSTOMER");
        splitCancel.add("CANCELLED_BY_RESTAURANT");
        return List.of(
                new Change("add optional field promisedBy", s -> s.with("promisedBy", Field.string("").optional()),
                        Map.of(), List.of()),
                new Change("add status READY_FOR_PICKUP", s -> s.change("status", f -> f.withEnumValues(withReady)),
                        Map.of(), List.of("READY_FOR_PICKUP")),
                new Change("rename eta to etaMinutes", s -> s.without("eta").with("etaMinutes",
                        Field.integer("minutes until delivery")), Map.of("eta", "etaMinutes"), List.of()),
                new Change("riderName absent until a rider is assigned", s -> s.change("riderName", Field::optional),
                        Map.of(), List.of()),
                new Change("totalPaise sent as a string (decimal safety)", s -> s.change("totalPaise",
                        f -> f.withType(Type.STRING)), Map.of(), List.of()),
                new Change("remove deprecated legacyCode (nobody reads it)", s -> s.without("legacyCode"),
                        Map.of(), List.of()),
                new Change("add restaurant.cuisine", s -> s.change("restaurant",
                        f -> f.withObject(f.object().with("cuisine", Field.string("").optional()))), Map.of(), List.of()),
                new Change("etaMinutes now counts to pickup, not delivery", s -> s.change("etaMinutes",
                        f -> f.withMeaning("minutes until pickup")), Map.of(), List.of()),
                new Change("placedAt as epoch millis", s -> s.change("placedAt", f -> f.withType(Type.INT)
                        .withMeaning("epoch millis")), Map.of(), List.of()),
                new Change("remove restaurant.area (moved to /restaurants)", s -> s.change("restaurant",
                        f -> f.withObject(f.object().without("area"))), Map.of(), List.of()),
                new Change("unknown order: 200 with empty body instead of 404", s -> s.change("@unknownOrder",
                        f -> f.withMeaning("200 with an empty body")), Map.of(), List.of()),
                new Change("split CANCELLED into BY_CUSTOMER / BY_RESTAURANT", s -> s.change("status",
                        f -> f.withEnumValues(splitCancel)), Map.of(), List.of("CANCELLED_BY_CUSTOMER")));
    }

    /**
     * The consumers as they were just before a change: up to date with every earlier version (they
     * read the current field names, types, optionality and meanings, and know the current status
     * values and fields). Each lists the fields it reads and whether it needs them.
     */
    static List<Consumer> consumers(Schema current, Map<String, String> renamesSoFar) {
        List<String> statuses = current.at("status").enumValues();
        List<String> fields = allPaths(current, "");
        return List.of(
                consumer("mobile-app", current, renamesSoFar, statuses, fields, List.of(), true, true, true,
                        "id", "status", "eta", "riderName", "restaurant.name", "@unknownOrder"),
                consumer("web-app", current, renamesSoFar, statuses, fields, List.of(), false, false, false,
                        "id", "status", "restaurant.name", "restaurant.area", "totalPaise", "@unknownOrder"),
                consumer("notification-service", current, renamesSoFar, statuses, fields,
                        List.of("PICKED_UP", "DELIVERED", "CANCELLED"), false, true, true,
                        "id", "status", "riderName?", "eta", "@unknownOrder"),
                consumer("analytics", current, renamesSoFar, statuses, fields, List.of(), false, false, true,
                        "id", "status", "totalPaise", "placedAt"),
                consumer("support-console", current, renamesSoFar, statuses, fields, List.of(), false, false, false,
                        "id", "status", "riderName?"));
    }

    /** {@code reads}: field paths as of v1; a trailing {@code ?} means "used if present". */
    private static Consumer consumer(String name, Schema current, Map<String, String> renames, List<String> statuses,
                                     List<String> fields, List<String> actsOn, boolean strictUnknown,
                                     boolean strictEnums, boolean contract, String... reads) {
        Map<String, Consumer.Expectation> map = new LinkedHashMap<>();
        for (String read : reads) {
            boolean optional = read.endsWith("?");
            String v1Name = optional ? read.substring(0, read.length() - 1) : read;
            String field = renames.getOrDefault(v1Name, v1Name);
            Field f = current.at(field);
            if (f == null) {
                continue;                                  // removed earlier: the consumer stopped reading it
            }
            Type type = f.type() == Type.ENUM && !strictEnums ? Type.STRING : f.type();
            map.put(field, new Consumer.Expectation(type, !optional && f.alwaysPresent(), f.meaning()));
        }
        return new Consumer(name, map, statuses, fields, actsOn, strictUnknown, strictEnums, contract);
    }

    static List<String> allPaths(Schema s, String prefix) {
        List<String> out = new ArrayList<>();
        for (var e : s.fields().entrySet()) {
            if (e.getKey().startsWith("@")) {
                continue;                                  // behaviour, not a field in the payload
            }
            out.add(prefix + e.getKey());
            if (e.getValue().object() != null) {
                out.addAll(allPaths(e.getValue().object(), prefix + e.getKey() + "."));
            }
        }
        return out;
    }

    /** Runs the history; {@code tolerantReaders} makes every consumer ignore unknown fields and enum values. */
    public static List<Verdict> run(boolean tolerantReaders) {
        List<Verdict> verdicts = new ArrayList<>();
        Schema current = v1();
        Map<String, String> renames = new LinkedHashMap<>();
        for (Change change : changes()) {
            List<Consumer> consumers = consumers(current, renames);
            Schema next = change.apply().apply(current);
            List<String> sent = new ArrayList<>(List.of(ContractChecks.EXAMPLE_STATUS));
            sent.addAll(change.newStatusesSent());
            List<String> broken = new ArrayList<>();
            List<String> contractFailures = new ArrayList<>();
            Map<String, List<String>> problems = new LinkedHashMap<>();
            for (Consumer c : consumers) {
                Consumer reader = tolerantReaders ? c.tolerant() : c;
                List<String> p = reader.problemsReading(next, sent);
                if (!p.isEmpty()) {
                    broken.add(c.name());
                    problems.put(c.name(), p);
                }
                ContractChecks.verify(c, next).forEach(f -> contractFailures.add(c.name() + ": " + f));
            }
            verdicts.add(new Verdict(change, broken, StructuralDiff.worst(StructuralDiff.compare(current, next)),
                    contractFailures, problems));
            renames.putAll(change.renames());
            current = next;
        }
        return verdicts;
    }

    public static void main(String[] args) {
        List<Verdict> verdicts = run(false);
        List<Verdict> tolerant = run(true);
        System.out.println("12 changes to GET /orders/{id}; 5 consumers (2 strict on unknown fields or enums, 3 with contracts)\n");
        System.out.printf("%-3s %-50s %-30s %-30s %-6s %-9s%n", "#", "change", "breaks (as built)",
                "breaks (tolerant readers)", "diff", "contracts");
        for (int i = 0; i < verdicts.size(); i++) {
            Verdict v = verdicts.get(i);
            System.out.printf("%-3d %-50s %-30s %-30s %-6s %-9s%n", i + 1, v.change().description(), names(v),
                    names(tolerant.get(i)), v.diff(), v.caughtByContracts() ? "FAIL" : "pass");
        }
        summary("as built", verdicts);
        summary("tolerant readers", tolerant);
        System.out.println("\nwhy (as built):");
        verdicts.forEach(v -> v.problems().forEach((c, p) ->
                System.out.println("  " + v.change().description() + " -> " + c + ": " + p)));
    }

    private static String names(Verdict v) {
        return v.brokenConsumers().isEmpty() ? "-" : String.join(", ", v.brokenConsumers())
                .replace("notification-service", "notification").replace("mobile-app", "mobile");
    }

    static void summary(String label, List<Verdict> verdicts) {
        long breaking = verdicts.stream().filter(Verdict::breaksSomeone).count();
        long diff = verdicts.stream().filter(v -> v.breaksSomeone() && v.caughtByDiff()).count();
        long diffFalse = verdicts.stream().filter(v -> !v.breaksSomeone() && v.caughtByDiff()).count();
        long cdc = verdicts.stream().filter(v -> v.breaksSomeone() && v.caughtByContracts()).count();
        long either = verdicts.stream().filter(v -> v.breaksSomeone() && (v.caughtByDiff() || v.caughtByContracts())).count();
        System.out.printf("%n%-17s %d of 12 changes break a consumer; diff fails the build for %d of them "
                + "(plus %d that broke nobody); contracts for %d; either: %d; missed by both: %d%n", label + ":", breaking,
                diff, diffFalse, cdc, either, breaking - either);
    }
}
