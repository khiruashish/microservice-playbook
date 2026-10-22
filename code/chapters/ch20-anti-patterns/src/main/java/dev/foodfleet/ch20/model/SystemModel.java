package dev.foodfleet.ch20.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What an architecture review needs to know about a system, in one value: services, how they call
 * each other per user request, who owns and who touches which tables, which shared libraries at
 * which versions, and when each service was deployed. Read from a plain-text model file that lives
 * in the repository next to the code (see {@link ModelParser}).
 */
public record SystemModel(String name, List<Service> services, List<Call> calls, List<Table> tables,
                          List<Library> libraries, List<Deploy> deploys) {

    /** @param endpoints public API operations; {@code loc} lines of production code */
    public record Service(String name, String team, int endpoints, int loc) {
    }

    /**
     * @param perRequest calls made per user request that reaches {@code from} (12 = once per item)
     * @param sync       true for a blocking call (HTTP/gRPC), false for an event/message
     */
    public record Call(String from, String to, double perRequest, boolean sync) {
    }

    /** @param writers services that write it besides the owner; {@code readers} that read it directly */
    public record Table(String name, String owner, Set<String> writers, Set<String> readers) {
    }

    /** @param kind "utility" (logging, HTTP helpers) or "domain" (shared business classes) */
    public record Library(String name, String kind, Map<String, String> versionByService) {
    }

    public record Deploy(LocalDateTime at, Set<String> services) {
    }

    public SystemModel {
        services = List.copyOf(services);
        calls = List.copyOf(calls);
        tables = List.copyOf(tables);
        libraries = List.copyOf(libraries);
        deploys = List.copyOf(deploys);
    }

    public Optional<Service> service(String name) {
        return services.stream().filter(s -> s.name().equals(name)).findFirst();
    }
}
