import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Problem: client-side load balancing across instances: round robin, smooth weighted round robin, least connections; skip unhealthy. */
public class LoadBalancer {

    static final class Instance {
        final String id; final int weight; volatile boolean healthy = true;
        final AtomicInteger activeConnections = new AtomicInteger();
        int currentWeight;                           // state for smooth weighted round robin
        Instance(String id, int weight) { this.id = id; this.weight = weight; }
        public String toString() { return id; }
    }

    interface Strategy { Instance pick(List<Instance> healthy); }

    static final class RoundRobin implements Strategy {
        private final AtomicInteger next = new AtomicInteger();
        public Instance pick(List<Instance> healthy) {
            return healthy.get(Math.floorMod(next.getAndIncrement(), healthy.size()));   // floorMod: safe after int overflow
        }
    }

    /** Nginx's smooth WRR: weights 5,1,1 give a,a,b,a,c,a,a instead of a,a,a,a,a,b,c (no bursts). */
    static final class SmoothWeighted implements Strategy {
        public synchronized Instance pick(List<Instance> healthy) {
            int total = 0;
            Instance best = null;
            for (Instance i : healthy) {
                i.currentWeight += i.weight;
                total += i.weight;
                if (best == null || i.currentWeight > best.currentWeight) best = i;
            }
            best.currentWeight -= total;
            return best;
        }
    }

    static final class LeastConnections implements Strategy {
        public Instance pick(List<Instance> healthy) {
            return Collections.min(healthy, Comparator.comparingInt((Instance i) -> i.activeConnections.get()).thenComparing(i -> i.id));
        }
    }

    static final class Balancer {
        private final List<Instance> instances; private final Strategy strategy;
        Balancer(List<Instance> instances, Strategy strategy) { this.instances = instances; this.strategy = strategy; }
        Instance choose() {
            List<Instance> healthy = instances.stream().filter(i -> i.healthy).toList();
            if (healthy.isEmpty()) throw new IllegalStateException("no healthy instances");
            return strategy.pick(healthy);
        }
    }

    static String picks(Balancer b, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(b.choose().id);
        return sb.toString();
    }

    public static void main(String[] args) {
        Instance a = new Instance("a", 5), b = new Instance("b", 1), c = new Instance("c", 1);
        List<Instance> all = List.of(a, b, c);

        String rr = picks(new Balancer(all, new RoundRobin()), 6);
        String wrr = picks(new Balancer(all, new SmoothWeighted()), 7);
        System.out.println("round robin       : " + rr);
        System.out.println("smooth weighted   : " + wrr + "  (weights a=5 b=1 c=1)");
        check(rr.equals("abcabc"), "round robin");
        check(wrr.equals("aabacaa"), "smooth WRR interleaves");

        b.healthy = false;
        String skip = picks(new Balancer(all, new RoundRobin()), 4);
        System.out.println("b unhealthy, RR   : " + skip);
        check(!skip.contains("b"), "unhealthy skipped");
        b.healthy = true;

        a.activeConnections.set(10); b.activeConnections.set(2); c.activeConnections.set(5);
        Balancer lc = new Balancer(all, new LeastConnections());
        Instance chosen = lc.choose();
        System.out.println("least connections : " + chosen + " (a=10, b=2, c=5 active)");
        check(chosen == b, "least connections");
        System.out.println("PASS");
    }

    static void check(boolean ok, String what) { if (!ok) { System.out.println("FAILED: " + what); System.exit(1); } }
}
