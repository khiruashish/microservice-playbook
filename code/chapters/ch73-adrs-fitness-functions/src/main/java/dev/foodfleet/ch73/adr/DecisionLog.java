package dev.foodfleet.ch73.adr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A decision log read from one of three formats, and checks over it: the log's own fitness functions.
 * <ul>
 *   <li>{@link Format#NYGARD}: one file per decision as adr-tools writes it ({@code # 1. Title}, {@code Date:},
 *       {@code ## Status}, {@code ## Context}, {@code ## Decision}, {@code ## Consequences})</li>
 *   <li>{@link Format#MADR}: one file per decision ({@code status:} and {@code date:} in optional front matter,
 *       {@code ## Context and Problem Statement}, {@code ## Considered Options}, {@code ## Decision Outcome})</li>
 *   <li>{@link Format#HANDBOOK}: this handbook's admonitions, {@code !!! decision "ADR-NNN: title"} with
 *       {@code **Status:**}, {@code **Date:**}, {@code **Context.**}, {@code **Decision.**}, {@code **Consequences**}</li>
 * </ul>
 */
public final class DecisionLog {

    public enum Format {
        NYGARD(List.of("Context", "Decision", "Consequences")),
        MADR(List.of("Context and Problem Statement", "Considered Options", "Decision Outcome")),
        HANDBOOK(List.of("Context", "Decision", "Consequences"));

        public final List<String> required;

        Format(List<String> required) {
            this.required = required;
        }
    }

    public record Adr(int number, String title, String status, LocalDate date, Set<String> sections, Set<Integer> references,
                      Set<Integer> supersedes, Integer supersededBy, String source) {
    }

    public record Finding(String rule, int adr, String detail) {
    }

    public static final Set<String> STATUSES = Set.of("proposed", "accepted", "rejected", "deprecated", "superseded");

    public final Format format;
    public final Map<Integer, Adr> adrs = new TreeMap<>();
    public final List<String> duplicates = new ArrayList<>();

    private DecisionLog(Format format) {
        this.format = format;
    }

    private static final Pattern FILE_REF = Pattern.compile("\\((?:\\./)?(\\d{4})-[^)]*\\.md\\)");
    private static final Pattern ADR_REF = Pattern.compile("ADR-(\\d{3,4})");
    private static final Pattern DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    /** Reads every {@code NNNN-*.md} in a directory. */
    public static DecisionLog readFiles(Path dir, Format format) throws IOException {
        DecisionLog log = new DecisionLog(format);
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.getFileName().toString().matches("\\d{4}-.*\\.md")).sorted().toList()) {
                String text = Files.readString(f);
                int number = Integer.parseInt(f.getFileName().toString().substring(0, 4));
                log.add(format == Format.NYGARD ? nygard(number, text, f) : madr(number, text, f));
            }
        }
        return log;
    }

    /** Reads every {@code !!! decision "ADR-NNN: ..."} block in the Markdown files under a directory. */
    public static DecisionLog readHandbook(Path docs) throws IOException {
        DecisionLog log = new DecisionLog(Format.HANDBOOK);
        Pattern block = Pattern.compile("!!! decision \"ADR-(\\d{3}): ([^\"]*)\"\\n((?: {4}.*\\n|\\n)+)");
        try (Stream<Path> files = Files.walk(docs)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".md")).sorted().toList()) {
                Matcher m = block.matcher(Files.readString(f));
                while (m.find()) {
                    int number = Integer.parseInt(m.group(1));
                    String body = m.group(3);
                    Matcher st = Pattern.compile("\\*\\*Status:\\*\\* ([A-Za-z ]+)").matcher(body);
                    Matcher dt = Pattern.compile("\\*\\*Date:\\*\\* " + DATE.pattern()).matcher(body);
                    Set<String> sections = new LinkedHashSet<>();
                    for (String s : Format.HANDBOOK.required) {
                        if (body.contains("**" + s + ".**") || body.contains("**" + s + ":**") || body.contains("**" + s + "**")) {
                            sections.add(s);
                        }
                    }
                    Set<Integer> refs = refs(ADR_REF, m.group(2) + "\n" + body);
                    refs.remove(number);
                    log.add(new Adr(number, m.group(2), st.find() ? st.group(1).trim() : null, dt.find() ? LocalDate.parse(dt.group(1)) : null,
                            sections, refs, Set.of(), null, docs.relativize(f).toString()));
                }
            }
        }
        return log;
    }

    static Adr nygard(int number, String text, Path f) {
        Map<String, String> sections = sections(text);
        String statusText = sections.getOrDefault("Status", "").trim();
        String status = statusText.isEmpty() ? null : statusText.lines().filter(l -> !l.isBlank()).findFirst().orElse("").trim();
        Set<Integer> supersedes = new TreeSet<>();
        Integer supersededBy = null;
        for (String line : statusText.lines().toList()) {
            Matcher r = FILE_REF.matcher(line);
            if (r.find()) {
                if (line.startsWith("Superseded by")) {
                    supersededBy = Integer.parseInt(r.group(1));
                } else if (line.startsWith("Supersedes")) {
                    supersedes.add(Integer.parseInt(r.group(1)));
                }
            }
        }
        if (status != null && status.startsWith("Superseded by")) {
            status = "Superseded";
        }
        Matcher d = Pattern.compile("(?m)^Date: " + DATE.pattern()).matcher(text);
        // links in the Status section (Amended by, Superseded by, ...) are added to old records later, by design
        Set<Integer> refs = refs(FILE_REF, text.replace(statusText, ""));
        refs.remove(number);
        String title = text.lines().filter(l -> l.startsWith("# ")).findFirst().orElse("").replaceFirst("^# \\d+\\. ", "");
        return new Adr(number, title, status, d.find() ? LocalDate.parse(d.group(1)) : null, present(sections, Format.NYGARD), refs, supersedes, supersededBy,
                f.getFileName().toString());
    }

    static Adr madr(int number, String text, Path f) {
        String front = "";
        if (text.startsWith("---\n")) {
            int end = text.indexOf("\n---", 4);
            front = text.substring(4, end);
        }
        Matcher st = Pattern.compile("(?m)^status: *\"?([^\"\\n]*)\"?").matcher(front);
        Matcher dt = Pattern.compile("(?m)^date: *" + DATE.pattern()).matcher(front);
        String status = st.find() && !st.group(1).isBlank() ? st.group(1).trim() : null;
        Integer supersededBy = null;
        if (status != null && status.toLowerCase().startsWith("superseded by")) {
            Matcher r = ADR_REF.matcher(status);
            supersededBy = r.find() ? Integer.parseInt(r.group(1)) : null;
            status = "superseded";
        }
        Set<Integer> refs = refs(FILE_REF, text);
        refs.remove(number);
        String title = text.lines().filter(l -> l.startsWith("# ")).findFirst().orElse("").substring(2);
        return new Adr(number, title, status, dt.find() ? LocalDate.parse(dt.group(1)) : null, present(sections(text), Format.MADR), refs, Set.of(),
                supersededBy, f.getFileName().toString());
    }

    private void add(Adr a) {
        if (adrs.putIfAbsent(a.number(), a) != null) {
            duplicates.add(String.valueOf(a.number()));
        }
    }

    static Map<String, String> sections(String text) {
        Map<String, String> out = new TreeMap<>();
        String current = null;
        StringBuilder body = new StringBuilder();
        for (String line : text.lines().toList()) {
            if (line.startsWith("## ")) {
                if (current != null) {
                    out.put(current, body.toString());
                }
                current = line.substring(3).trim();
                body.setLength(0);
            } else if (current != null) {
                body.append(line).append('\n');
            }
        }
        if (current != null) {
            out.put(current, body.toString());
        }
        return out;
    }

    static Set<String> present(Map<String, String> sections, Format f) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : f.required) {
            if (sections.keySet().stream().anyMatch(k -> k.equalsIgnoreCase(s))) {
                out.add(s);
            }
        }
        return out;
    }

    static Set<Integer> refs(Pattern p, String text) {
        Set<Integer> out = new TreeSet<>();
        Matcher m = p.matcher(text);
        while (m.find()) {
            out.add(Integer.parseInt(m.group(1)));
        }
        return out;
    }

    /** The log's fitness functions, L1 to L7. */
    public List<Finding> lint() {
        List<Finding> out = new ArrayList<>();
        for (String d : duplicates) {
            out.add(new Finding("L3 numbering", Integer.parseInt(d), "number used twice"));
        }
        int expected = adrs.isEmpty() ? 0 : adrs.keySet().iterator().next();
        for (int n : adrs.keySet()) {
            for (; expected < n; expected++) {
                out.add(new Finding("L3 numbering", expected, "missing from the sequence"));
            }
            expected = n + 1;
        }
        Set<Integer> outOfOrder = outOfOrder();
        for (Adr a : adrs.values()) {
            List<String> missing = new ArrayList<>(format.required);
            missing.removeAll(a.sections());
            if (!missing.isEmpty()) {
                out.add(new Finding("L1 sections", a.number(), "missing " + String.join(", ", missing)));
            }
            if (a.status() == null) {
                out.add(new Finding("L2 status", a.number(), "no status"));
            } else if (!STATUSES.contains(a.status().toLowerCase())) {
                out.add(new Finding("L2 status", a.number(), "unknown status: " + a.status()));
            }
            if (a.date() == null) {
                out.add(new Finding("L4 date", a.number(), "no date"));
            } else if (outOfOrder.contains(a.number())) {
                out.add(new Finding("L5 chronology", a.number(), "dated " + a.date() + ", out of order with its neighbours"));
            }
            for (int r : a.references()) {
                Adr target = adrs.get(r);
                if (target == null) {
                    out.add(new Finding("L6 references", a.number(), "refers to " + r + ", which isn't in the log"));
                } else if (a.date() != null && target.date() != null && target.date().isAfter(a.date()) && !outOfOrder.contains(a.number())
                        && !outOfOrder.contains(r)) {
                    out.add(new Finding("L6 references", a.number(), "its text refers to " + r + ", decided later (" + target.date() + "): edited after acceptance"));
                }
            }
            if ("superseded".equalsIgnoreCase(a.status()) && (a.supersededBy() == null || !adrs.containsKey(a.supersededBy()))) {
                out.add(new Finding("L7 supersession", a.number(), "superseded, but its successor isn't named or isn't in the log"));
            }
            for (int s : a.supersedes()) {
                Adr old = adrs.get(s);
                if (old != null && !"superseded".equalsIgnoreCase(old.status())) {
                    out.add(new Finding("L7 supersession", s, "superseded by " + a.number() + " but still " + old.status()));
                }
            }
        }
        return out;
    }

    /**
     * The fewest decisions whose dates must change for dates to rise with numbers: everything outside one longest
     * non-decreasing run of dates, taken in number order.
     */
    public Set<Integer> outOfOrder() {
        List<Adr> dated = adrs.values().stream().filter(a -> a.date() != null).toList();
        int n = dated.size();
        int[] len = new int[n];
        int[] prev = new int[n];
        int best = -1;
        for (int i = 0; i < n; i++) {
            len[i] = 1;
            prev[i] = -1;
            for (int j = 0; j < i; j++) {
                if (!dated.get(j).date().isAfter(dated.get(i).date()) && len[j] + 1 > len[i]) {
                    len[i] = len[j] + 1;
                    prev[i] = j;
                }
            }
            if (best < 0 || len[i] > len[best]) {
                best = i;
            }
        }
        Set<Integer> keep = new TreeSet<>();
        for (int i = best; i >= 0; i = prev[i]) {
            keep.add(dated.get(i).number());
        }
        Set<Integer> out = new TreeSet<>();
        dated.forEach(a -> {
            if (!keep.contains(a.number())) {
                out.add(a.number());
            }
        });
        return out;
    }

    /** Which decisions are named by code under {@code root} (in *.java files), and where. */
    public static Map<Integer, Set<String>> mentionedInCode(Path root) throws IOException {
        Map<Integer, Set<String>> out = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java") && !p.toString().contains("/build/")).toList()) {
                Matcher m = ADR_REF.matcher(Files.readString(f));
                while (m.find()) {
                    out.computeIfAbsent(Integer.parseInt(m.group(1)), k -> new TreeSet<>()).add(root.relativize(f).getName(0).toString());
                }
            }
        }
        return out;
    }
}
