import dev.foodfleet.ch41.privacy.CryptoShredder;
import dev.foodfleet.ch41.privacy.InMemoryKeyStore;
import dev.foodfleet.ch41.sbom.Component;
import dev.foodfleet.ch41.sbom.CycloneDx;
import dev.foodfleet.ch41.vuln.Advisory.Severity;
import dev.foodfleet.ch41.vuln.Gate;
import dev.foodfleet.ch41.vuln.OsvDatabase;
import dev.foodfleet.ch41.vuln.Scanner;
import dev.foodfleet.ch41.vuln.Scanner.Finding;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Chapter 41 measurements.
 *   scan  &lt;advisory-db dir&gt; &lt;today&gt; &lt;version=release-date=sbom.cdx.json&gt;...
 *   shred
 */
public final class Measure {

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "scan" -> scan(Path.of(args[1]), LocalDate.parse(args[2]), List.of(args).subList(3, args.length));
            case "shred" -> shred();
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    record Release(String version, LocalDate date, List<Component> components) {
    }

    static void scan(Path dbDir, LocalDate today, List<String> specs) throws Exception {
        long t0 = System.nanoTime();
        OsvDatabase db = OsvDatabase.load(dbDir, "Maven");
        System.out.printf("advisory database: %d Maven advisories loaded in %.1f s%n", db.size(), (System.nanoTime() - t0) / 1e9);
        List<Release> releases = new ArrayList<>();
        for (String s : specs) {
            String[] p = s.split("=", 3);
            releases.add(new Release(p[0], LocalDate.parse(p[1]), CycloneDx.components(Files.readString(Path.of(p[2])))));
        }
        Scanner scanner = new Scanner(db);

        System.out.println();
        System.out.println("findings as of " + today + " (advisories that affect a component's exact version)");
        System.out.printf("%-9s %-11s %-11s %-9s %-6s %-9s %-6s %-11s %-13s %-15s %s%n", "Keycloak", "released", "components",
                "critical", "high", "moderate", "low", "packages", "fix available", "in dependencies", "scan time");
        Map<String, List<Finding>> now = new HashMap<>();
        for (Release r : releases) {
            long s0 = System.nanoTime();
            List<Finding> f = scanner.scan(r.components(), null);
            double ms = (System.nanoTime() - s0) / 1e6;
            now.put(r.version(), f);
            Map<Severity, Integer> by = new EnumMap<>(Severity.class);
            f.forEach(x -> by.merge(x.advisory().severity(), 1, Integer::sum));
            Set<String> pkgs = new LinkedHashSet<>();
            f.forEach(x -> pkgs.add(x.component().mavenName()));
            long fixable = f.stream().filter(x -> x.fixedIn() != null).count();
            long deps = f.stream().filter(x -> !x.component().mavenName().startsWith("org.keycloak:")).count();
            System.out.printf("%-9s %-11s %-11d %-9d %-6d %-9d %-6d %-11d %-13s %-15s %.0f ms%n", r.version(), r.date(),
                    r.components().size(), by.getOrDefault(Severity.CRITICAL, 0), by.getOrDefault(Severity.HIGH, 0),
                    by.getOrDefault(Severity.MODERATE, 0), by.getOrDefault(Severity.LOW, 0), pkgs.size(),
                    fixable + " of " + f.size(), deps + " of " + f.size(), ms);
        }

        System.out.println();
        System.out.println("the same artifacts, findings known at each point in time (critical + high in brackets)");
        StringBuilder head = new StringBuilder(String.format("%-9s", "Keycloak"));
        String[] points = {"at release", "+3 months", "+6 months", "+1 year", "+2 years", "today"};
        for (String p : points) {
            head.append(String.format(" %-14s", p));
        }
        System.out.println(head);
        for (Release r : releases) {
            StringBuilder row = new StringBuilder(String.format("%-9s", r.version()));
            LocalDate[] at = {r.date(), r.date().plusMonths(3), r.date().plusMonths(6), r.date().plusYears(1),
                    r.date().plusYears(2), today};
            for (LocalDate d : at) {
                if (d.isAfter(today)) {
                    row.append(String.format(" %-14s", "-"));
                    continue;
                }
                Instant knownBy = d.atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
                List<Finding> f = scanner.scan(r.components(), knownBy);
                long serious = f.stream().filter(x -> x.advisory().severity() == Severity.CRITICAL
                        || x.advisory().severity() == Severity.HIGH).count();
                row.append(String.format(" %-14s", f.size() + " (" + serious + ")"));
            }
            System.out.println(row);
        }

        System.out.println();
        System.out.println("CI gate (fail on critical/high with a fix available), today:");
        Gate gate = new Gate(Severity.HIGH, List.of());
        for (Release r : releases) {
            Gate.Result g = gate.evaluate(now.get(r.version()), today);
            System.out.printf("  Keycloak %-8s %-6s blocking %-4d reported without blocking %d%n", r.version(),
                    g.passed() ? "pass" : "FAIL", g.blocking().size(), g.reported().size());
        }

        Release latest = releases.get(releases.size() - 1);
        System.out.println();
        System.out.println("Keycloak " + latest.version() + ", critical and high findings today:");
        for (Finding f : now.get(latest.version())) {
            if (f.advisory().severity() == Severity.CRITICAL || f.advisory().severity() == Severity.HIGH) {
                System.out.printf("  %-8s %-20s %-52s fixed in %s%n", f.advisory().severity(), f.advisory().displayId(),
                        f.component().mavenName() + "@" + f.component().version(), Optional.ofNullable(f.fixedIn()).orElse("(none)"));
            }
        }
        Release oldest = releases.get(0);
        System.out.println();
        System.out.println("Keycloak " + oldest.version() + ", critical findings today:");
        for (Finding f : now.get(oldest.version())) {
            if (f.advisory().severity() == Severity.CRITICAL) {
                System.out.printf("  %-20s %-52s published %s, fixed in %s%n", f.advisory().displayId(),
                        f.component().mavenName() + "@" + f.component().version(),
                        f.advisory().published().toString().substring(0, 10), Optional.ofNullable(f.fixedIn()).orElse("(none)"));
            }
        }
    }

    static void shred() throws Exception {
        KeyGenerator g = KeyGenerator.getInstance("AES");
        g.init(256);
        SecretKey kek = g.generateKey();
        InMemoryKeyStore keys = new InMemoryKeyStore();
        CryptoShredder shredder = new CryptoShredder(kek, keys, 1_000);
        int customers = 20_000;
        String[] fields = {"name", "phone", "address"};
        String[] values = {"Asha Rao", "+91 98450 12345", "No. 12/3, 1st Cross, 5th Main, HSR Layout, Bengaluru 560102"};
        // the primary copy plus what can't be edited: a Kafka topic, last night's backup, the analytics extract
        Map<String, String[]> primary = new HashMap<>();
        long t0 = System.nanoTime();
        for (int c = 0; c < customers; c++) {
            String[] row = new String[fields.length];
            for (int f = 0; f < fields.length; f++) {
                row[f] = shredder.encrypt("cust-" + c, fields[f], values[f]);
            }
            primary.put("cust-" + c, row);
        }
        double encUs = (System.nanoTime() - t0) / 1e3 / (customers * fields.length);
        Map<String, String[]> backup = new HashMap<>(primary);
        Map<String, String[]> topic = new HashMap<>(primary);
        int plainBytes = 0, cipherBytes = 0;
        for (int f = 0; f < fields.length; f++) {
            plainBytes += values[f].getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            cipherBytes += primary.get("cust-0")[f].length();
        }

        // read back: warm (cached data keys) and cold (each key unwrapped)
        long t1 = System.nanoTime();
        for (int c = 0; c < customers; c++) {
            for (int f = 0; f < fields.length; f++) {
                shredder.decrypt("cust-" + c, fields[f], primary.get("cust-" + c)[f]);
            }
        }
        double decUs = (System.nanoTime() - t1) / 1e3 / (customers * fields.length);

        // 1,000 customers ask to be erased
        int erased = 1_000;
        long t2 = System.nanoTime();
        for (int c = 0; c < erased; c++) {
            shredder.erase("cust-" + c);
        }
        double eraseUs = (System.nanoTime() - t2) / 1e3 / erased;
        int readableErased = 0, readableOthers = 0;
        for (Map<String, String[]> copy : List.of(primary, backup, topic)) {
            for (int c = 0; c < customers; c++) {
                for (int f = 0; f < fields.length; f++) {
                    if (shredder.decrypt("cust-" + c, fields[f], copy.get("cust-" + c)[f]).isPresent()) {
                        if (c < erased) {
                            readableErased++;
                        } else {
                            readableOthers++;
                        }
                    }
                }
            }
        }
        System.out.printf("%d customers x %d personal fields, encrypted with a data key per customer (AES-256-GCM),%n", customers, fields.length);
        System.out.println("copied as written into three places: the orders table, a Kafka topic, last night's backup");
        System.out.printf("  encrypt: %.1f µs per field; decrypt: %.1f µs per field (data-key cache of 1,000)%n", encUs, decUs);
        System.out.printf("  size: %d bytes of plaintext became %d bytes of stored text per customer%n", plainBytes, cipherBytes);
        System.out.printf("  erase %d customers: %.1f µs each (delete one key row)%n", erased, eraseUs);
        System.out.printf("  readable afterwards, erased customers: %d of %d fields; everyone else: %d of %d%n",
                readableErased, erased * fields.length * 3, readableOthers, (customers - erased) * fields.length * 3);
        System.out.printf("  key table: %d rows of %d bytes%n", keys.size(), keys.get("cust-" + erased).orElseThrow().length);
    }
}
