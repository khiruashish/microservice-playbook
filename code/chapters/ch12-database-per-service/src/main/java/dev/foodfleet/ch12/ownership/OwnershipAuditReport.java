package dev.foodfleet.ch12.ownership;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plain-Java CLI. With no arguments it audits the bundled FoodFleet 2023 sample; otherwise
 * {@code <table-owners.txt> <statements.txt>}.
 */
public final class OwnershipAuditReport {

    public static void main(String[] args) {
        List<String> owners = args.length == 2 ? read(Path.of(args[0])) : resource("/sql/table-owners.txt");
        List<String> statements = args.length == 2 ? read(Path.of(args[1])) : resource("/sql/foodfleet-2023-statements.txt");
        System.out.print(render(parseOwners(owners), OwnershipAudit.parseLog(statements)));
    }

    public static Map<String, String> parseOwners(List<String> lines) {
        Map<String, String> owners = new HashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Expected table = service: " + raw);
            }
            owners.put(parts[0].strip().toLowerCase(Locale.ROOT), parts[1].strip());
        }
        return owners;
    }

    public static String render(Map<String, String> owners, List<OwnershipAudit.Statement> statements) {
        OwnershipAudit.Report report = new OwnershipAudit(owners).audit(statements);
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "%d statements, %d touch another service's tables%n%n",
                report.statementsChecked(),
                report.violations().stream().map(OwnershipAudit.Violation::statement).distinct().count()));
        out.append("== Cross-service access (reader -> owner) ==\n");
        report.couplingByPair().forEach((pair, n) -> out.append(String.format(Locale.ROOT, "%-40s %2d%n", pair, n)));
        if (!report.unknownTables().isEmpty()) {
            out.append("\nTables with no owner: ").append(String.join(", ", report.unknownTables())).append('\n');
        }
        return out.toString();
    }

    private static List<String> read(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> resource(String name) {
        try (InputStream in = OwnershipAuditReport.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
