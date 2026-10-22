package dev.foodfleet.ch12.ownership;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch12.ownership.OwnershipAudit.Access;
import dev.foodfleet.ch12.ownership.OwnershipAudit.Statement;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OwnershipAuditTest {

    private final OwnershipAudit audit = new OwnershipAudit(Map.of(
            "orders", "ordering", "order_line", "ordering", "menu_item", "catalog", "refund", "payment"));

    @Test
    void extractsReadsAndWrites() {
        TableReferences.Refs refs = TableReferences.of(
                "UPDATE orders SET status = 'X' WHERE id IN (SELECT order_id FROM order_line ol JOIN menu_item m ON m.id = ol.menu_item_id)");

        assertThat(refs.writes()).containsExactly("orders");
        assertThat(refs.reads()).containsExactlyInAnyOrder("order_line", "menu_item");
    }

    @Test
    void deleteIsAWriteAndSchemaPrefixesResolve() {
        assertThat(TableReferences.of("DELETE FROM refund WHERE status = 'EXPIRED'").reads()).isEmpty();
        assertThat(audit.ownerOf("public.orders")).isEqualTo("ordering");
    }

    @Test
    void flagsOnlyOtherServicesTables() {
        OwnershipAudit.Report report = audit.audit(List.of(
                new Statement("ordering", "SELECT price_paise FROM menu_item WHERE id = $1"),
                new Statement("ordering", "INSERT INTO orders (id) VALUES ($1)"),
                new Statement("kitchen", "UPDATE orders SET status = 'PREPARING' WHERE id = $1"),
                new Statement("kitchen", "SELECT * FROM kitchen_ticket")));

        assertThat(report.violations()).extracting(OwnershipAudit.Violation::service, OwnershipAudit.Violation::owner,
                        OwnershipAudit.Violation::access)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ordering", "catalog", Access.READ),
                        org.assertj.core.groups.Tuple.tuple("kitchen", "ordering", Access.WRITE));
        assertThat(report.unknownTables()).containsExactly("kitchen_ticket");
    }

    @Test
    void foodFleet2023SharedDatabase() throws IOException {
        OwnershipAudit.Report report = new OwnershipAudit(OwnershipAuditReport.parseOwners(resource("/sql/table-owners.txt")))
                .audit(OwnershipAudit.parseLog(resource("/sql/foodfleet-2023-statements.txt")));

        assertThat(report.statementsChecked()).isEqualTo(23);
        assertThat(report.violations().stream().map(OwnershipAudit.Violation::statement).distinct()).hasSize(14);
        assertThat(report.couplingByPair())
                .containsEntry("kitchen -> ordering (write)", 1)
                .containsEntry("payment -> ordering (write)", 1)
                .containsEntry("ordering -> promotions (write)", 1)
                .containsEntry("reporting -> payment (read)", 2);
        assertThat(report.unknownTables()).isEmpty();
    }

    private static List<String> resource(String name) throws IOException {
        try (InputStream in = OwnershipAuditTest.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }
}
