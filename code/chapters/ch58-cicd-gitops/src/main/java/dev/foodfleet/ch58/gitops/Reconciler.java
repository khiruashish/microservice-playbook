package dev.foodfleet.ch58.gitops;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * A minimal GitOps controller, the loop Argo CD and Flux run: every interval, fetch the environment repository; render
 * it; apply what's rendered with server-side apply, taking back fields someone changed by hand; delete what Git no
 * longer has. The cluster converges on Git, whether Git changed or the cluster did.
 */
public final class Reconciler {

    /** The outside world: Git, the renderer and the cluster. */
    public interface Ops {
        /** Fetches and returns the commit the branch points at. */
        String fetch();

        /** {@code kustomize build} of the path, at the fetched commit. */
        String render();

        /** True if applying the YAML would change anything ({@code kubectl diff --server-side}). */
        boolean drifted(String yaml);

        /** Server-side apply, as the controller's field manager, forcing conflicts. */
        void apply(String yaml);

        void delete(Inventory.Ref ref);

        /** The inventory recorded by the last successful sync. */
        Set<Inventory.Ref> loadInventory();

        void saveInventory(Set<Inventory.Ref> refs, String commit);
    }

    public enum Action { NONE, APPLIED_COMMIT, REVERTED_DRIFT }

    public record Result(Action action, String commit, List<String> pruned, long millis) {
    }

    private final Ops ops;
    private final String namespace;
    private final LongSupplier clock;
    private String lastCommit;

    public Reconciler(Ops ops, String namespace, LongSupplier clock) {
        this.ops = ops;
        this.namespace = namespace;
        this.clock = clock;
    }

    public Result reconcile() {
        long start = clock.getAsLong();
        String commit = ops.fetch();
        String yaml = ops.render();
        boolean newCommit = !commit.equals(lastCommit);
        boolean drift = !newCommit && ops.drifted(yaml);
        if (!newCommit && !drift) {
            return new Result(Action.NONE, commit, List.of(), clock.getAsLong() - start);
        }
        Set<Inventory.Ref> rendered = Inventory.parse(yaml, namespace);
        ops.apply(yaml);
        Set<Inventory.Ref> prune = new TreeSet<>(Inventory.toPrune(ops.loadInventory(), rendered));
        prune.forEach(ops::delete);
        ops.saveInventory(rendered, commit);
        lastCommit = commit;
        return new Result(newCommit ? Action.APPLIED_COMMIT : Action.REVERTED_DRIFT, commit,
                prune.stream().map(Inventory.Ref::resource).collect(Collectors.toList()), clock.getAsLong() - start);
    }
}
