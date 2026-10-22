package dev.foodfleet.ch06.decomposition;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** A candidate split: which service each capability lives in. */
public record Decomposition(String name, Map<String, String> serviceOf) {

    public Decomposition {
        serviceOf = Map.copyOf(serviceOf);
    }

    /** Builds a decomposition from "service → capabilities" groups, the way people draw it. */
    public static Decomposition of(String name, Map<String, List<String>> services) {
        Map<String, String> serviceOf = new TreeMap<>();
        services.forEach((service, capabilities) -> capabilities.forEach(capability -> {
            if (serviceOf.put(capability, service) != null) {
                throw new IllegalArgumentException(capability + " assigned to more than one service");
            }
        }));
        return new Decomposition(name, serviceOf);
    }

    public String serviceOf(String capability) {
        String service = serviceOf.get(capability);
        if (service == null) {
            throw new IllegalArgumentException(name + ": capability " + capability + " is not assigned to a service");
        }
        return service;
    }

    public Collection<String> services() {
        return new TreeSet<>(serviceOf.values());
    }

    void requireCovers(DomainModel model) {
        model.capabilities().forEach(c -> serviceOf(c.name()));
    }
}
