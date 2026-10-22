package dev.foodfleet.ch11.simulation;

import dev.foodfleet.ch11.simulation.Stage.Fixed;
import dev.foodfleet.ch11.simulation.Stage.LeaseExpiry;
import dev.foodfleet.ch11.simulation.Stage.Periodic;
import java.util.List;

/**
 * The setups compared in chapter 11. Timings are the documented defaults where they exist
 * (Eureka, Spring Cloud LoadBalancer's 35 s cache, Kubernetes probe defaults, the JDK's 30 s DNS
 * cache) and stated assumptions otherwise (propagation delays, health-check intervals).
 */
public final class Mechanisms {

    private Mechanisms() {
    }

    /** Client-side discovery with Eureka and Spring Cloud LoadBalancer, all defaults. */
    public static Mechanism eurekaDefaults() {
        return new Mechanism("Eureka, defaults", List.of(
                new LeaseExpiry("lease expires (heartbeat 30 s, lease 90 s, counted twice)", 30, 90, true),
                new Periodic("eviction sweep (60 s)", 60),
                new Periodic("server response cache (30 s)", 30),
                new Periodic("client registry fetch (30 s)", 30),
                new Periodic("load-balancer instance cache (35 s)", 35)), false);
    }

    /** Same, tuned for fast failure detection (more heartbeat traffic on the registry). */
    public static Mechanism eurekaTuned() {
        return new Mechanism("Eureka, tuned", List.of(
                new LeaseExpiry("lease expires (heartbeat 5 s, lease 15 s, counted twice)", 5, 15, true),
                new Periodic("eviction sweep (5 s)", 5),
                new Periodic("server response cache (disabled)", 0),
                new Periodic("client registry fetch (5 s)", 5),
                new Periodic("load-balancer instance cache (5 s)", 5)), false);
    }

    /** DNS-based discovery with external health checks (e.g. Consul DNS, cloud DNS failover). */
    public static Mechanism dnsWithHealthChecks() {
        return new Mechanism("DNS + health checks", List.of(
                new Periodic("first failed health check (every 10 s)", 10),
                new Fixed("two more failed checks (threshold 3)", 20),
                new Periodic("record TTL left in resolver caches (30 s)", 30),
                new Periodic("JVM DNS cache (30 s default)", 30)), false);
    }

    /** Kubernetes Service (ClusterIP), container process exits: the kubelet sees it at once. */
    public static Mechanism kubernetesCrash() {
        return new Mechanism("Kubernetes Service, crash", List.of(
                new Fixed("kubelet sees the container exit (assumed 1 s)", 1),
                new Fixed("EndpointSlice update reaches kube-proxy on every node (assumed 2 s)", 2)), false);
    }

    /** Kubernetes Service, process alive but not answering: only the readiness probe notices. */
    public static Mechanism kubernetesHang() {
        return new Mechanism("Kubernetes Service, hang", List.of(
                new Periodic("first failed readiness probe (period 10 s)", 10),
                new Fixed("two more failed probes (failureThreshold 3)", 20),
                new Fixed("EndpointSlice update reaches kube-proxy (assumed 2 s)", 2)), false);
    }

    public static List<Mechanism> all() {
        return List.of(eurekaDefaults(), eurekaTuned(), dnsWithHealthChecks(), kubernetesCrash(),
                kubernetesHang(), kubernetesHang().withRetries());
    }
}
