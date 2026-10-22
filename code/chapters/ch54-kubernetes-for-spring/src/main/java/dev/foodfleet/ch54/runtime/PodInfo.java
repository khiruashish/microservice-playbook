package dev.foodfleet.ch54.runtime;

import java.util.Map;

/**
 * Where this pod runs, from the Downward API (environment variables set from fieldRef / resourceFieldRef in the pod
 * spec): for log fields, metrics tags and sizing thread pools to the CPU actually granted rather than the node's.
 *
 * @param name      metadata.name
 * @param namespace metadata.namespace
 * @param node      spec.nodeName
 * @param cpuLimitMillis resources.limits.cpu in millicores (divisor 1m); 0 when not set
 * @param memoryLimitBytes resources.limits.memory in bytes; 0 when not set
 */
public record PodInfo(String name, String namespace, String node, long cpuLimitMillis, long memoryLimitBytes) {

    public static PodInfo from(Map<String, String> env) {
        return new PodInfo(env.getOrDefault("POD_NAME", "local"), env.getOrDefault("POD_NAMESPACE", "local"),
                env.getOrDefault("NODE_NAME", "local"), parse(env.get("CPU_LIMIT_MILLIS")), parse(env.get("MEMORY_LIMIT_BYTES")));
    }

    /**
     * Threads worth running CPU-bound work on: the CPU limit rounded up, at least 1. The kubelet sets the Downward API
     * value of an unset limit to the node's allocatable CPU, so this is never larger than what the pod may use.
     */
    public int cpuBoundThreads(int availableProcessors) {
        if (cpuLimitMillis <= 0) {
            return availableProcessors;
        }
        return (int) Math.max(1, Math.min(availableProcessors, (cpuLimitMillis + 999) / 1000));
    }

    private static long parse(String v) {
        if (v == null || v.isBlank()) {
            return 0;
        }
        return Long.parseLong(v.trim());
    }
}
