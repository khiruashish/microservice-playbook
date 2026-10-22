package dev.foodfleet.ch74.roadmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Commits as the sets of contexts they changed, from {@code git log --no-merges --name-only --format=@%h}. Like Code
 * Maat, commits that touch more than {@link #MAX_CHANGESET} files are left out (renames, reformatting, license headers),
 * and only main sources count.
 */
public final class History {

    public static final int MAX_CHANGESET = 30;

    public final List<Set<String>> commits = new ArrayList<>();
    public int read;
    public int tooLarge;
    public int noMainSources;

    public static History parse(String gitLog, Contexts contexts) {
        History h = new History();
        List<String> files = null;
        for (String line : (gitLog + "\n@end").lines().toList()) {
            if (line.startsWith("@")) {
                if (files != null) {
                    h.add(files, contexts);
                }
                files = new ArrayList<>();
            } else if (!line.isBlank() && files != null) {
                files.add(line.trim());
            }
        }
        return h;
    }

    private void add(List<String> files, Contexts contexts) {
        read++;
        if (files.size() > MAX_CHANGESET) {
            tooLarge++;
            return;
        }
        Set<String> touched = new TreeSet<>();
        for (String f : files) {
            String c = contexts.ofPath(f);
            if (c != null) {
                touched.add(c);
            }
        }
        if (touched.isEmpty()) {
            noMainSources++;
        } else {
            commits.add(touched);
        }
    }

    public int revisions(String context) {
        return (int) commits.stream().filter(c -> c.contains(context)).count();
    }

    public int shared(String a, String b) {
        return (int) commits.stream().filter(c -> c.contains(a) && c.contains(b)).count();
    }

    /** Code Maat's degree of coupling: shared revisions over the average revisions of the two, as a percentage. */
    public double degree(String a, String b) {
        double avg = (revisions(a) + revisions(b)) / 2.0;
        return avg == 0 ? 0 : 100.0 * shared(a, b) / avg;
    }
}
