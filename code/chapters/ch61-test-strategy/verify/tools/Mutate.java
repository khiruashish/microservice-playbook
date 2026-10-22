import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Chapter 61's experiment: mutation analysis by hand. Each line of mutants.tsv is a realistic bug: a find-and-replace
 * on order-service's source. For each, the main code is recompiled with the bug and every test suite runs against it
 * (the test classes stay compiled against the correct code, as in CI). A suite "kills" a mutant if any of its tests
 * fails. The baseline (no bug) runs first: a suite that fails on correct code can't be trusted on bugs either, and
 * counts as catching a bug only if more of its tests fail.
 * Usage: Mutate <main sources dir> <mutants.tsv> <work dir> <test classpath> <suites...>
 */
public final class Mutate {

    record Mutant(String id, String file, String kind, String description, String find, String replace) {
    }

    record SuiteResult(int tests, int failed, long ms, String failures) {
    }

    public static void main(String[] args) throws Exception {
        Path src = Path.of(args[0]);
        Path work = Path.of(args[2]);
        String testCp = args[3];
        List<String> suites = List.of(args).subList(4, args.length);
        List<Mutant> mutants = new ArrayList<>();
        mutants.add(new Mutant("-", "", "", "no bug (baseline)", "", ""));
        for (String line : Files.readAllLines(Path.of(args[1]))) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] p = line.split("\t");
            mutants.add(new Mutant(p[0], p[1], p[2], p[3], p[4].replace("\\n", "\n"), p[5].replace("\\n", "\n")));
        }
        Map<String, int[]> kills = new LinkedHashMap<>();
        Map<String, Long> time = new LinkedHashMap<>();
        Map<String, Integer> tests = new LinkedHashMap<>();
        Map<String, Integer> baseline = new LinkedHashMap<>();
        suites.forEach(s -> kills.put(s, new int[2]));
        StringBuilder header = new StringBuilder(String.format("%-4s %-12s %-42s", "bug", "kind", "description"));
        suites.forEach(s -> header.append(String.format(" %-10s", s)));
        System.out.println(header);
        for (Mutant m : mutants) {
            Path dir = work.resolve("m-" + m.id().replace("-", "base"));
            copy(src, dir.resolve("src"));
            if (!m.file().isEmpty()) {
                Path f;
                try (Stream<Path> found = Files.walk(dir.resolve("src"))) {
                    f = found.filter(x -> x.getFileName().toString().equals(m.file())).findFirst().orElseThrow();
                }
                String text = Files.readString(f);
                if (!text.contains(m.find())) {
                    throw new IllegalStateException(m.id() + ": text not found");
                }
                Files.writeString(f, text.replace(m.find(), m.replace()));
            }
            Path classes = dir.resolve("classes");
            Files.createDirectories(classes);
            List<String> javac = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "javac").toString(), "-nowarn", "-d", classes.toString()));
            try (Stream<Path> files = Files.walk(dir.resolve("src"))) {
                files.filter(x -> x.toString().endsWith(".java")).forEach(x -> javac.add(x.toString()));
            }
            Process pc = new ProcessBuilder(javac).redirectErrorStream(true).start();
            String compileOut = new String(pc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            StringBuilder row = new StringBuilder(String.format("%-4s %-12s %-42s", m.id(), m.kind(), m.description()));
            if (pc.waitFor() != 0) {
                row.append(" doesn't compile: ").append(compileOut.lines().findFirst().orElse(""));
                System.out.println(row);
                continue;
            }
            List<String> cmd = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Duser.timezone=UTC",
                    "-Dff.pg.url=" + System.getProperty("ff.pg.url", "jdbc:postgresql://127.0.0.1:5461/"), "-cp",
                    classes + java.io.File.pathSeparator + src.resolve("../resources").normalize() + java.io.File.pathSeparator + testCp, "RunSuites"));
            cmd.addAll(suites);
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            pb.environment().remove("JAVA_TOOL_OPTIONS");
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            Map<String, SuiteResult> results = parse(out);
            for (String s : suites) {
                SuiteResult r = results.get(s);
                if (r == null) {
                    row.append(String.format(" %-10s", "error"));
                    continue;
                }
                // a suite that already fails on correct code catches a bug only by failing more tests than that
                boolean killed = r.failed() > baseline.getOrDefault(s, 0);
                if (m.id().equals("-")) {
                    row.append(String.format(" %-10s", r.failed() == 0 ? "pass" : "FAILS " + r.failed() + "/" + r.tests()));
                    time.put(s, r.ms());
                    baseline.put(s, r.failed());
                    tests.put(s, r.tests());
                } else {
                    row.append(String.format(" %-10s", killed ? "caught" : "-"));
                    kills.get(s)[killed ? 0 : 1]++;
                }
            }
            System.out.println(row);
            dropTestDatabases();
            if (System.getProperty("verbose") != null) {
                System.out.println("    " + out.replace("\n", "\n    "));
            }
        }
        System.out.println();
        System.out.println("suite       tests  run time  bugs caught (of " + (mutants.size() - 1) + ")");
        for (String s : suites) {
            System.out.printf("%-11s %-6d %-9s %d%n", s, tests.getOrDefault(s, 0), time.get(s) + " ms", kills.get(s)[0]);
        }
    }

    /** The suites create a database per test class; drop them between bugs. Needs the PostgreSQL driver on the classpath. */
    static void dropTestDatabases() throws Exception {
        String server = System.getProperty("ff.pg.url", "jdbc:postgresql://127.0.0.1:5461/");
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(server + "postgres", "postgres", "");
             java.sql.ResultSet rs = c.createStatement().executeQuery("select datname from pg_database where datname like 'orders\\_%'")) {
            List<String> names = new ArrayList<>();
            while (rs.next()) {
                names.add(rs.getString(1));
            }
            for (String n : names) {
                c.createStatement().execute("drop database " + n + " with (force)");
            }
        }
    }

    static final Pattern LINE = Pattern.compile("(?m)^(\\S+) tests=(\\d+) failed=(\\d+) ms=(\\d+) failures=(.*)$");

    static Map<String, SuiteResult> parse(String out) {
        Map<String, SuiteResult> m = new LinkedHashMap<>();
        Matcher x = LINE.matcher(out);
        while (x.find()) {
            m.put(x.group(1), new SuiteResult(Integer.parseInt(x.group(2)), Integer.parseInt(x.group(3)), Long.parseLong(x.group(4)), x.group(5)));
        }
        return m;
    }

    static void copy(Path from, Path to) throws IOException {
        try (Stream<Path> files = Files.walk(from)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                Path t = to.resolve(from.relativize(f).toString());
                if (Files.isDirectory(f)) {
                    Files.createDirectories(t);
                } else {
                    Files.copy(f, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
