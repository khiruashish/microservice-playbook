package dev.foodfleet.ch07.topology;

import static dev.foodfleet.ch07.topology.Topology.Complexity.COMPLEX;
import static dev.foodfleet.ch07.topology.Topology.Complexity.COMPLICATED;
import static dev.foodfleet.ch07.topology.Topology.Complexity.SIMPLE;
import static dev.foodfleet.ch07.topology.Topology.Mode.COLLABORATION;
import static dev.foodfleet.ch07.topology.Topology.Mode.FACILITATING;
import static dev.foodfleet.ch07.topology.Topology.Mode.X_AS_A_SERVICE;
import static dev.foodfleet.ch07.topology.Topology.TeamType.COMPLICATED_SUBSYSTEM;
import static dev.foodfleet.ch07.topology.Topology.TeamType.ENABLING;
import static dev.foodfleet.ch07.topology.Topology.TeamType.LAYER;
import static dev.foodfleet.ch07.topology.Topology.TeamType.PLATFORM;
import static dev.foodfleet.ch07.topology.Topology.TeamType.STREAM_ALIGNED;

import dev.foodfleet.ch07.topology.Topology.Component;
import dev.foodfleet.ch07.topology.Topology.Dependency;
import dev.foodfleet.ch07.topology.Topology.Domain;
import dev.foodfleet.ch07.topology.Topology.Feature;
import dev.foodfleet.ch07.topology.Topology.Interaction;
import dev.foodfleet.ch07.topology.Topology.Team;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FoodFleet's organisation twice: in 2023, with teams organised by technology layer around a shared
 * database, and in 2026, after the reorganisation to stream-aligned teams that followed chapter 6's
 * decomposition. The software is the same; only ownership, data ownership and interactions change.
 */
public final class FoodFleetTeams {

    private FoodFleetTeams() {
    }

    /** Back-end services that run on the platform and ship telemetry. */
    private static final List<String> SERVICES = List.of(
            "ordering", "catalog", "customer", "kitchen", "delivery", "dispatch-optimizer",
            "zone", "promotions", "notification", "payment");

    static List<Domain> domains() {
        return List.of(
                new Domain("Ordering", COMPLICATED),
                new Domain("Restaurant catalog", SIMPLE),
                new Domain("Kitchen", SIMPLE),
                new Domain("Delivery logistics", COMPLICATED),
                new Domain("Dispatch optimisation", COMPLEX),
                new Domain("Promotions", COMPLICATED),
                new Domain("Customer engagement", SIMPLE),
                new Domain("Payments", COMPLICATED),
                new Domain("Runtime platform", COMPLICATED),
                new Domain("Observability", COMPLICATED),
                new Domain("Shared data", COMPLICATED));
    }

    /** Component → path and domain; owners are assigned per topology. */
    private static final Map<String, String[]> LAYOUT = Map.ofEntries(
            Map.entry("app-checkout", new String[] {"apps/customer-app/checkout/", "Ordering"}),
            Map.entry("app-discovery", new String[] {"apps/customer-app/discovery/", "Restaurant catalog"}),
            Map.entry("app-tracking", new String[] {"apps/customer-app/tracking/", "Delivery logistics"}),
            Map.entry("app-shell", new String[] {"apps/customer-app/shell/", "Runtime platform"}),
            Map.entry("partner-portal", new String[] {"apps/partner-portal/", "Restaurant catalog"}),
            Map.entry("api-gateway", new String[] {"platform/api-gateway/", "Runtime platform"}),
            Map.entry("k8s-platform", new String[] {"platform/k8s/", "Runtime platform"}),
            Map.entry("observability", new String[] {"platform/observability/", "Observability"}),
            Map.entry("ordering", new String[] {"services/ordering/", "Ordering"}),
            Map.entry("catalog", new String[] {"services/catalog/", "Restaurant catalog"}),
            Map.entry("customer", new String[] {"services/customer/", "Customer engagement"}),
            Map.entry("kitchen", new String[] {"services/kitchen/", "Kitchen"}),
            Map.entry("delivery", new String[] {"services/delivery/", "Delivery logistics"}),
            Map.entry("dispatch-optimizer", new String[] {"services/dispatch-optimizer/", "Dispatch optimisation"}),
            Map.entry("zone", new String[] {"services/zone/", "Delivery logistics"}),
            Map.entry("promotions", new String[] {"services/promotions/", "Promotions"}),
            Map.entry("notification", new String[] {"services/notification/", "Customer engagement"}),
            Map.entry("payment", new String[] {"services/payment/", "Payments"}),
            Map.entry("shared-database", new String[] {"db/shared/", "Shared data"}));

    private static Component component(String name, String owner, String dataOwner) {
        String[] layout = LAYOUT.get(name);
        return new Component(name, layout[0], owner, layout[1], dataOwner);
    }

    /** Runtime dependencies; identical in both topologies apart from the shared database. */
    static List<Dependency> dependencies(boolean sharedDatabase) {
        List<Dependency> deps = new ArrayList<>(List.of(
                sync("app-checkout", "api-gateway"), sync("app-discovery", "api-gateway"),
                sync("app-tracking", "api-gateway"), sync("partner-portal", "api-gateway"),
                sync("app-checkout", "app-shell"), sync("app-discovery", "app-shell"), sync("app-tracking", "app-shell"),
                sync("api-gateway", "ordering"), sync("api-gateway", "catalog"),
                sync("api-gateway", "kitchen"), sync("api-gateway", "delivery"),
                sync("ordering", "catalog"), sync("ordering", "customer"), sync("ordering", "promotions"),
                sync("ordering", "payment"), sync("ordering", "zone"),
                async("ordering", "kitchen"), async("ordering", "delivery"), async("ordering", "notification"),
                sync("delivery", "dispatch-optimizer"), sync("delivery", "zone"), sync("catalog", "zone")));
        for (String service : SERVICES) {
            deps.add(sync(service, "k8s-platform"));
            deps.add(async(service, "observability"));
            if (sharedDatabase) {
                deps.add(sync(service, "shared-database"));
            }
        }
        return deps;
    }

    /** 2023: teams by technology layer, one shared database owned by DBAs. */
    public static Topology before() {
        List<Team> teams = List.of(
                new Team("Mobile", LAYER, 7),
                new Team("Web", LAYER, 4),
                new Team("Backend A", LAYER, 14),
                new Team("Backend B", LAYER, 11),
                new Team("DBA", LAYER, 3),
                new Team("Ops", LAYER, 6));
        List<Component> components = List.of(
                component("app-checkout", "Mobile", null),
                component("app-discovery", "Mobile", null),
                component("app-tracking", "Mobile", null),
                component("app-shell", "Mobile", null),
                component("partner-portal", "Web", null),
                component("api-gateway", "Ops", null),
                component("k8s-platform", "Ops", null),
                component("observability", "Ops", null),
                component("ordering", "Backend A", "DBA"),
                component("catalog", "Backend A", "DBA"),
                component("customer", "Backend A", "DBA"),
                component("kitchen", "Backend A", "DBA"),
                component("promotions", "Backend A", "DBA"),
                component("delivery", "Backend B", "DBA"),
                component("dispatch-optimizer", "Backend B", "DBA"),
                component("zone", "Backend B", "DBA"),
                component("notification", "Backend B", "DBA"),
                component("payment", "Backend B", "DBA"),
                component("shared-database", "DBA", "DBA"));
        LocalDate y21 = LocalDate.of(2021, 1, 1);
        List<Interaction> interactions = List.of(
                new Interaction("Mobile", "Backend A", COLLABORATION, LocalDate.of(2022, 6, 1)),
                new Interaction("Mobile", "Backend B", COLLABORATION, LocalDate.of(2022, 6, 1)),
                new Interaction("Web", "Backend A", COLLABORATION, LocalDate.of(2022, 9, 1)),
                new Interaction("Backend A", "Backend B", COLLABORATION, LocalDate.of(2022, 1, 1)),
                new Interaction("Backend A", "DBA", X_AS_A_SERVICE, y21),
                new Interaction("Backend B", "DBA", X_AS_A_SERVICE, y21),
                new Interaction("Ops", "Backend A", X_AS_A_SERVICE, y21),
                new Interaction("Ops", "Backend B", X_AS_A_SERVICE, y21));
        return new Topology("FoodFleet 2023 (layer teams)", LocalDate.of(2023, 10, 1),
                teams, domains(), components, dependencies(true), interactions);
    }

    /** 2026: stream-aligned teams around chapter 6's capabilities; each service owns its data. */
    public static Topology after() {
        List<Team> teams = List.of(
                new Team("Checkout", STREAM_ALIGNED, 8),
                new Team("Restaurant Partner", STREAM_ALIGNED, 7),
                new Team("Logistics", STREAM_ALIGNED, 7),
                new Team("Growth", STREAM_ALIGNED, 6),
                new Team("Payments", COMPLICATED_SUBSYSTEM, 5),
                new Team("Dispatch", COMPLICATED_SUBSYSTEM, 5),
                new Team("Platform", PLATFORM, 8),
                new Team("SRE Enablement", ENABLING, 3));
        List<Component> components = List.of(
                component("app-checkout", "Checkout", null),
                component("ordering", "Checkout", "Checkout"),
                component("app-discovery", "Restaurant Partner", null),
                component("partner-portal", "Restaurant Partner", null),
                component("catalog", "Restaurant Partner", "Restaurant Partner"),
                component("kitchen", "Restaurant Partner", "Restaurant Partner"),
                component("app-tracking", "Logistics", null),
                component("delivery", "Logistics", "Logistics"),
                component("zone", "Logistics", "Logistics"),
                component("promotions", "Growth", "Growth"),
                component("customer", "Growth", "Growth"),
                component("notification", "Growth", "Growth"),
                component("payment", "Payments", "Payments"),
                component("dispatch-optimizer", "Dispatch", "Dispatch"),
                component("app-shell", "Platform", null),
                component("api-gateway", "Platform", null),
                component("k8s-platform", "Platform", null),
                component("observability", "Platform", null));
        LocalDate reorg = LocalDate.of(2024, 4, 1);
        List<Interaction> interactions = List.of(
                new Interaction("Checkout", "Restaurant Partner", X_AS_A_SERVICE, reorg),
                new Interaction("Checkout", "Growth", X_AS_A_SERVICE, reorg),
                new Interaction("Checkout", "Payments", X_AS_A_SERVICE, reorg),
                new Interaction("Checkout", "Logistics", X_AS_A_SERVICE, reorg),
                new Interaction("Restaurant Partner", "Logistics", X_AS_A_SERVICE, reorg),
                new Interaction("Logistics", "Growth", X_AS_A_SERVICE, reorg),
                new Interaction("Logistics", "Dispatch", COLLABORATION, LocalDate.of(2025, 11, 1)),
                new Interaction("Platform", "Checkout", X_AS_A_SERVICE, reorg),
                new Interaction("Platform", "Restaurant Partner", X_AS_A_SERVICE, reorg),
                new Interaction("Platform", "Logistics", X_AS_A_SERVICE, reorg),
                new Interaction("Platform", "Growth", X_AS_A_SERVICE, reorg),
                new Interaction("Platform", "Payments", X_AS_A_SERVICE, reorg),
                new Interaction("SRE Enablement", "Logistics", FACILITATING, LocalDate.of(2026, 4, 1)),
                new Interaction("SRE Enablement", "Dispatch", FACILITATING, LocalDate.of(2026, 5, 1)));
        return new Topology("FoodFleet 2026 (Team Topologies)", LocalDate.of(2026, 6, 1),
                teams, domains(), components, dependencies(false), interactions);
    }

    /** Eight features from one quarter's roadmap; the same work is measured against both org designs. */
    public static List<Feature> features() {
        return List.of(
                new Feature("Note to restaurant", List.of("app-checkout", "ordering", "kitchen", "partner-portal"), true),
                new Feature("Coupon stacking", List.of("app-checkout", "promotions", "ordering"), true),
                new Feature("Live rider tracking", List.of("app-tracking", "delivery", "notification"), false),
                new Feature("Launch city: Pune", List.of("zone", "delivery", "catalog"), true),
                new Feature("Menu item photos", List.of("partner-portal", "catalog", "app-discovery"), true),
                new Feature("UPI Autopay", List.of("app-checkout", "payment", "ordering"), true),
                new Feature("Restaurant busy mode", List.of("partner-portal", "kitchen", "catalog", "app-discovery"), true),
                new Feature("ETA on the cart", List.of("app-checkout", "ordering", "delivery", "zone"), false));
    }

    private static Dependency sync(String from, String to) {
        return new Dependency(from, to, false);
    }

    private static Dependency async(String from, String to) {
        return new Dependency(from, to, true);
    }
}
