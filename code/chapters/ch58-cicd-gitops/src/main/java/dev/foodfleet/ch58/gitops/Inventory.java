package dev.foodfleet.ch58.gitops;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What a GitOps controller owns: the objects in the rendered manifests. Anything it applied before that's no longer
 * rendered (a file deleted from Git) is pruned. The set it applied last is kept in the cluster, so a restarted
 * controller still knows what it owns; Argo CD and Flux keep the same kind of inventory.
 */
public final class Inventory {

    public record Ref(String kind, String namespace, String name) implements Comparable<Ref> {

        /** The form kubectl takes: {@code kind/name}. */
        public String resource() {
            return kind.toLowerCase() + "/" + name;
        }

        public String encoded() {
            return kind + "." + namespace + "." + name;
        }

        public static Ref decode(String s) {
            String[] p = s.split("\\.", 3);
            return new Ref(p[0], p[1], p[2]);
        }

        @Override
        public int compareTo(Ref o) {
            return encoded().compareTo(o.encoded());
        }
    }

    private Inventory() {
    }

    /** The objects in multi-document YAML, as {@code kustomize build} prints it (kind, metadata.name, metadata.namespace). */
    public static Set<Ref> parse(String yaml, String defaultNamespace) {
        Set<Ref> refs = new LinkedHashSet<>();
        for (String doc : yaml.split("(?m)^---\\s*$")) {
            String kind = null;
            String name = null;
            String namespace = defaultNamespace;
            boolean inMetadata = false;
            for (String line : doc.split("\n")) {
                if (line.startsWith("kind:")) {
                    kind = line.substring(5).trim();
                } else if (line.startsWith("metadata:")) {
                    inMetadata = true;
                } else if (!line.startsWith(" ") && !line.isBlank()) {
                    inMetadata = false;
                } else if (inMetadata && line.startsWith("  name:")) {
                    name = line.substring(7).trim();
                } else if (inMetadata && line.startsWith("  namespace:")) {
                    namespace = line.substring(12).trim();
                }
            }
            if (kind != null && name != null) {
                refs.add(new Ref(kind, namespace, name));
            }
        }
        return refs;
    }

    /** What was applied before and isn't rendered any more. */
    public static Set<Ref> toPrune(Set<Ref> previouslyApplied, Set<Ref> rendered) {
        Set<Ref> prune = new LinkedHashSet<>(previouslyApplied);
        prune.removeAll(rendered);
        return prune;
    }
}
