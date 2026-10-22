package dev.foodfleet.ch58;

import dev.foodfleet.ch58.delivery.CanaryAnalysis;
import dev.foodfleet.ch58.delivery.CanaryAnalysis.Thresholds;
import dev.foodfleet.ch58.delivery.CanaryAnalysis.Verdict;
import dev.foodfleet.ch58.delivery.CanaryAnalysis.Window;
import dev.foodfleet.ch58.gitops.Inventory;
import dev.foodfleet.ch58.gitops.Inventory.Ref;
import dev.foodfleet.ch58.gitops.Reconciler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryTest {

    static final String RENDERED = """
            apiVersion: v1
            kind: ConfigMap
            metadata:
              labels:
                name: not-this
              name: menu-config
              namespace: menu-prod
            ---
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: menu-service
            spec:
              template:
                metadata:
                  name: nor-this
            """;

    @Test
    void readsTheObjectsInRenderedManifests() {
        Set<Ref> refs = Inventory.parse(RENDERED, "default");
        assertThat(refs.size()).isEqualTo(2);
        assertThat(refs.contains(new Ref("ConfigMap", "menu-prod", "menu-config"))).isTrue();
        assertThat(refs.contains(new Ref("Deployment", "default", "menu-service"))).isTrue();
        assertThat(Ref.decode(new Ref("Service", "menu-prod", "menu.v2").encoded())).isEqualTo(new Ref("Service", "menu-prod", "menu.v2"));
    }

    @Test
    void prunesWhatGitNoLongerHas() {
        Set<Ref> before = Set.of(new Ref("ConfigMap", "m", "old-flags"), new Ref("Deployment", "m", "menu-service"));
        Set<Ref> now = Set.of(new Ref("Deployment", "m", "menu-service"));
        assertThat(Inventory.toPrune(before, now)).isEqualTo(Set.of(new Ref("ConfigMap", "m", "old-flags")));
    }

    /** A fake cluster and repository. */
    static final class Fake implements Reconciler.Ops {
        String commit = "a1";
        String yaml = RENDERED;
        boolean drift;
        final List<String> log = new ArrayList<>();
        Set<Ref> inventory = new LinkedHashSet<>();

        public String fetch() {
            return commit;
        }

        public String render() {
            return yaml;
        }

        public boolean drifted(String y) {
            return drift;
        }

        public void apply(String y) {
            log.add("apply");
            drift = false;
        }

        public void delete(Ref r) {
            log.add("delete " + r.resource());
        }

        public Set<Ref> loadInventory() {
            return inventory;
        }

        public void saveInventory(Set<Ref> refs, String c) {
            inventory = refs;
        }
    }

    @Test
    void appliesNewCommitsRevertsDriftAndOtherwiseDoesNothing() {
        Fake f = new Fake();
        Reconciler r = new Reconciler(f, "default", System::currentTimeMillis);
        assertThat(r.reconcile().action()).isEqualTo(Reconciler.Action.APPLIED_COMMIT);
        assertThat(r.reconcile().action()).isEqualTo(Reconciler.Action.NONE);
        f.drift = true;                                         // someone ran kubectl scale
        assertThat(r.reconcile().action()).isEqualTo(Reconciler.Action.REVERTED_DRIFT);
        f.commit = "b2";                                        // the ConfigMap's file was deleted from Git
        f.yaml = RENDERED.substring(RENDERED.indexOf("---") + 4);
        Reconciler.Result res = r.reconcile();
        assertThat(res.action()).isEqualTo(Reconciler.Action.APPLIED_COMMIT);
        assertThat(res.pruned()).isEqualTo(List.of("configmap/menu-config"));
        assertThat(f.log).isEqualTo(List.of("apply", "apply", "apply", "delete configmap/menu-config"));
    }

    static final Thresholds T = new Thresholds(0.01, 0.005, 250, 200);

    @Test
    void canaryWaitsForEnoughTrafficThenPromotes() {
        Window stable = new Window(5000, 2, 40);
        assertThat(CanaryAnalysis.judge(new Window(120, 0, 35), stable, T)).isEqualTo(Verdict.WAIT);
        assertThat(CanaryAnalysis.judge(new Window(600, 1, 38), stable, T)).isEqualTo(Verdict.PROMOTE);
    }

    @Test
    void canaryAbortsOnErrorsLatencyOrABurst() {
        Window stable = new Window(5000, 2, 40);
        assertThat(CanaryAnalysis.judge(new Window(600, 30, 38), stable, T)).isEqualTo(Verdict.ABORT);       // 5% errors
        assertThat(CanaryAnalysis.judge(new Window(600, 0, 900), stable, T)).isEqualTo(Verdict.ABORT);       // slow
        assertThat(CanaryAnalysis.judge(new Window(40, 12, 30), stable, T)).isEqualTo(Verdict.ABORT);        // 12 errors in 40: don't wait
        // 0.8% errors is under the absolute limit but 0.76 points worse than stable
        assertThat(CanaryAnalysis.judge(new Window(1000, 8, 38), stable, T)).isEqualTo(Verdict.ABORT);
    }
}
