package dev.foodfleet.ch31.merge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrSetTest {

    @Test
    void aConcurrentAddSurvivesARemoveThatHadNotSeenIt() {
        OrSet<String> mumbai = new OrSet<String>().add("biryani", "A1");
        OrSet<String> chennai = mumbai.copy();

        chennai.remove("biryani");                 // Chennai removes the biryani it can see...
        mumbai.add("biryani", "A2");               // ...while Mumbai adds another, concurrently

        mumbai.merge(chennai);
        chennai.merge(mumbai);
        assertThat(mumbai.elements()).isEqualTo(Set.of("biryani"));
        assertThat(chennai.elements()).isEqualTo(Set.of("biryani"));
    }

    @Test
    void aRemoveThatHadSeenEveryAddWins() {
        OrSet<String> a = new OrSet<String>().add("dosa", "A1");
        OrSet<String> b = new OrSet<String>().merge(a);
        b.remove("dosa");
        a.merge(b);
        assertThat(a.contains("dosa")).isFalse();
    }

    @Test
    void mergeIsCommutativeAssociativeAndIdempotent() {
        Random rng = new Random(31);
        for (int round = 0; round < 500; round++) {
            List<OrSet<Integer>> replicas = List.of(new OrSet<>(), new OrSet<>(), new OrSet<>());
            for (int op = 0; op < 30; op++) {
                OrSet<Integer> r = replicas.get(rng.nextInt(3));
                int item = rng.nextInt(5);
                if (rng.nextBoolean()) {
                    r.add(item, round + "-" + op);
                } else {
                    r.remove(item);
                }
                if (rng.nextInt(4) == 0) {                         // occasional partial sync
                    r.merge(replicas.get(rng.nextInt(3)));
                }
            }
            OrSet<Integer> a = replicas.get(0), b = replicas.get(1), c = replicas.get(2);
            OrSet<Integer> ab = a.copy().merge(b), ba = b.copy().merge(a);
            assertThat(ab.sameStateAs(ba)).isTrue();
            OrSet<Integer> abC = a.copy().merge(b).merge(c), aBc = a.copy().merge(b.copy().merge(c));
            assertThat(abC.sameStateAs(aBc)).isTrue();
            assertThat(a.copy().merge(a).sameStateAs(a)).isTrue();
            assertThat(abC.elements()).isEqualTo(aBc.elements());
        }
    }
}
