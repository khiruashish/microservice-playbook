package dev.foodfleet.ch70.cost;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FoodFleet's services as the cluster sees them (requests per pod, replicas, owning team), and the arithmetic of their
 * cost: how many nodes their requests need, and how a month's bill divides among teams.
 * <p>
 * Prices are OpenCost's default price list (its {@code configs/default.json}, "based on GCP us-central1"): $0.031611
 * per vCPU-hour and $0.004237 per GiB-hour on demand, $0.006655 and $0.000892 on spot. Real prices vary by provider,
 * region, instance family and discount; the comparisons hold, the totals are illustrative.
 */
public final class Fleet {

    public static final double CPU_HOUR = 0.031611;
    public static final double GIB_HOUR = 0.004237;
    public static final double SPOT_CPU_HOUR = 0.006655;
    public static final double SPOT_GIB_HOUR = 0.000892;
    public static final double HOURS_PER_MONTH = 730;

    /** One service: what each pod requests, how many run, and what each pod uses as a share of its request. */
    public record Service(String name, String team, String kind, int replicas, double cpu, double memGiB) {

        Service rightSized(Map<String, double[]> factorsByKind) {
            double[] f = factorsByKind.getOrDefault(kind, new double[] {1, 1});
            return new Service(name, team, kind, replicas, Math.max(0.025, cpu * f[0]), Math.max(0.25, memGiB * f[1]));
        }
    }

    public record Node(double cpu, double memGiB) {

        public double hourly(boolean spot) {
            return spot ? cpu * SPOT_CPU_HOUR + memGiB * SPOT_GIB_HOUR : cpu * CPU_HOUR + memGiB * GIB_HOUR;
        }
    }

    public record Packing(int nodes, double cpuRequested, double memRequested, double cpuShare, double memShare) {
    }

    private Fleet() {
    }

    /** {@code service team kind replicas cpu memGiB}, tab-separated, # for comments. */
    public static List<Service> load(Path tsv) throws IOException {
        List<Service> out = new ArrayList<>();
        for (String line : Files.readAllLines(tsv)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\t");
            out.add(new Service(f[0], f[1], f[2], Integer.parseInt(f[3]), Double.parseDouble(f[4]), Double.parseDouble(f[5])));
        }
        return out;
    }

    public static List<Service> rightSize(List<Service> fleet, Map<String, double[]> factorsByKind) {
        return fleet.stream().map(s -> s.rightSized(factorsByKind)).toList();
    }

    /**
     * First-fit decreasing, by each pod's larger share of a node (CPU or memory): roughly what the scheduler and a
     * node autoscaler such as Karpenter arrive at for pods that only state requests.
     */
    public static Packing pack(List<Service> fleet, Node allocatable) {
        List<double[]> pods = new ArrayList<>();
        for (Service s : fleet) {
            for (int i = 0; i < s.replicas(); i++) {
                pods.add(new double[] {s.cpu(), s.memGiB()});
            }
        }
        pods.sort(Comparator.comparingDouble((double[] p) -> Math.max(p[0] / allocatable.cpu(), p[1] / allocatable.memGiB())).reversed());
        List<double[]> nodes = new ArrayList<>(); // free cpu, free memory
        for (double[] p : pods) {
            double[] home = null;
            for (double[] n : nodes) {
                if (n[0] >= p[0] - 1e-9 && n[1] >= p[1] - 1e-9) {
                    home = n;
                    break;
                }
            }
            if (home == null) {
                home = new double[] {allocatable.cpu(), allocatable.memGiB()};
                nodes.add(home);
            }
            home[0] -= p[0];
            home[1] -= p[1];
        }
        double cpu = pods.stream().mapToDouble(p -> p[0]).sum();
        double mem = pods.stream().mapToDouble(p -> p[1]).sum();
        return new Packing(nodes.size(), cpu, mem, cpu / (nodes.size() * allocatable.cpu()), mem / (nodes.size() * allocatable.memGiB()));
    }

    /**
     * A month's cost per team, three ways: by requests, by usage, and by the larger of the two per pod (OpenCost's
     * rule). What no pod accounts for (the nodes' unrequested or unused capacity) is the idle cost, returned under "idle".
     *
     * @param usageByKind each kind's usage as a share of its request {cpu, memory}
     */
    public static Map<String, double[]> showback(List<Service> fleet, Map<String, double[]> usageByKind, double clusterMonthly) {
        Map<String, double[]> byTeam = new LinkedHashMap<>(); // request, usage, max(request, usage)
        double[] total = new double[3];
        for (Service s : fleet) {
            double[] u = usageByKind.getOrDefault(s.kind(), new double[] {1, 1});
            double request = s.replicas() * (s.cpu() * CPU_HOUR + s.memGiB() * GIB_HOUR) * HOURS_PER_MONTH;
            double usage = s.replicas() * (s.cpu() * u[0] * CPU_HOUR + s.memGiB() * u[1] * GIB_HOUR) * HOURS_PER_MONTH;
            double max = s.replicas() * (Math.max(s.cpu(), s.cpu() * u[0]) * CPU_HOUR + Math.max(s.memGiB(), s.memGiB() * u[1]) * GIB_HOUR) * HOURS_PER_MONTH;
            double[] t = byTeam.computeIfAbsent(s.team(), k -> new double[3]);
            t[0] += request;
            t[1] += usage;
            t[2] += max;
            total[0] += request;
            total[1] += usage;
            total[2] += max;
        }
        byTeam.put("idle", new double[] {clusterMonthly - total[0], clusterMonthly - total[1], clusterMonthly - total[2]});
        return byTeam;
    }
}
