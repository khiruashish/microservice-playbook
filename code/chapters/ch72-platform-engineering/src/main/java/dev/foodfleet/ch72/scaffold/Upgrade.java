package dev.foodfleet.ch72.scaffold;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Brings a service generated from one template version to the next, the way Copier's {@code update} does: for each
 * file, a three-way merge of the service's file ("ours") with the new template ("theirs") over the old template
 * ("base"), so the service's own edits survive. Uses {@code git merge-file}.
 */
public final class Upgrade {

    public enum Outcome { UNCHANGED, KEPT_LOCAL, UPDATED, MERGED, CONFLICT, ADDED }

    private Upgrade() {
    }

    /** Merges in place; returns each file's outcome. */
    public static Map<String, Outcome> apply(Path service, Map<String, String> oldTemplate, Map<String, String> newTemplate) throws Exception {
        Map<String, Outcome> out = new TreeMap<>();
        Path work = Files.createTempDirectory("upgrade");
        for (String file : new TreeSet<>(newTemplate.keySet())) {
            Path ours = service.resolve(file);
            String base = oldTemplate.get(file);
            String theirs = newTemplate.get(file);
            if (!Files.exists(ours)) {
                Files.createDirectories(ours.getParent());
                Files.writeString(ours, theirs);
                out.put(file, Outcome.ADDED);
                continue;
            }
            String mine = Files.readString(ours);
            if (base == null) {
                base = ""; // the file exists in the service but not in the old template: merge against nothing
            }
            if (base.equals(theirs)) {
                out.put(file, mine.equals(base) ? Outcome.UNCHANGED : Outcome.KEPT_LOCAL);
                continue;
            }
            if (mine.equals(base)) {
                Files.writeString(ours, theirs);
                out.put(file, Outcome.UPDATED);
                continue;
            }
            Path b = work.resolve("base");
            Path t = work.resolve("theirs");
            Files.writeString(b, base);
            Files.writeString(t, theirs);
            Process p = new ProcessBuilder("git", "merge-file", "-L", "service", "-L", "old template", "-L", "new template", ours.toString(), b.toString(), t.toString())
                    .redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            int conflicts = p.waitFor(); // the number of conflicts, 0 for a clean merge
            out.put(file, conflicts == 0 ? Outcome.MERGED : Outcome.CONFLICT);
        }
        try (var s = Files.walk(work)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
        } catch (IOException e) {
            // temporary files only
        }
        return out;
    }
}
