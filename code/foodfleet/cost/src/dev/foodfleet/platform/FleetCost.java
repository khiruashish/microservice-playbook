package dev.foodfleet.platform;

import dev.foodfleet.ch70.cost.Fleet;

import java.nio.file.Path;
import java.util.List;

/**
 * Prices FoodFleet's generated container fleet with chapter 70's {@link Fleet}: pods packed first-fit decreasing onto
 * 4-vCPU, 16 GiB nodes (3.8 CPUs and 14.5 GiB allocatable), at OpenCost's default prices. Compute only: managed Kafka,
 * PostgreSQL, Redis, load balancers and traffic are extra.
 */
public final class FleetCost {

    private FleetCost() {
    }

    public static void main(String[] args) throws Exception {
        // chapter 70's general-purpose node, and a compute-optimised shape with half the memory per CPU
        Fleet.Node[][] shapes = {{new Fleet.Node(4, 16), new Fleet.Node(3.8, 14.5)}, {new Fleet.Node(4, 8), new Fleet.Node(3.8, 6.5)}};
        System.out.println("| Fleet | Nodes of | Pods | CPU requested | Memory requested | Nodes | On demand, a month | All spot, a month |");
        System.out.println("|---|---|---|---|---|---|---|---|");
        for (Fleet.Node[] shape : shapes)
        for (String arg : args) {
            Fleet.Node node = shape[0];
            Fleet.Node allocatable = shape[1];
            List<Fleet.Service> fleet = Fleet.load(Path.of(arg));
            Fleet.Packing p = Fleet.pack(fleet, allocatable);
            int pods = fleet.stream().mapToInt(Fleet.Service::replicas).sum();
            double od = p.nodes() * node.hourly(false) * Fleet.HOURS_PER_MONTH;
            double spot = p.nodes() * node.hourly(true) * Fleet.HOURS_PER_MONTH;
            System.out.printf("| %s | %.0f vCPU, %.0f GiB | %d | %.1f CPUs | %.1f GiB | %d (CPU %.0f%%, memory %.0f%% requested) | $%,.0f | $%,.0f |%n",
                    arg.contains("min") ? "At the HPAs' minimums" : "At the HPAs' maximums", node.cpu(), node.memGiB(), pods, p.cpuRequested(), p.memRequested(),
                    p.nodes(), 100 * p.cpuShare(), 100 * p.memShare(), od, spot);
        }
    }
}
