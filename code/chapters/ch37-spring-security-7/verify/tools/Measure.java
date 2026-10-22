import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

/**
 * Spring Security 7.1.1's own password encoders, compiled from source by run.sh:
 *   encoders   time per hash and logins per second per core, by encoder and work factor
 *   upgrade    which stored hashes a DelegatingPasswordEncoder (bcrypt 12) still accepts and flags for upgrade
 */
public class Measure {

    public static void main(String[] args) {
        switch (args[0]) {
            case "encoders" -> encoders();
            case "upgrade" -> upgrade();
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    static double medianMillis(Runnable r, int n) {
        List<Long> t = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long s = System.nanoTime();
            r.run();
            t.add(System.nanoTime() - s);
        }
        t.sort(null);
        return t.get(n / 2) / 1e6;
    }

    static void encoders() {
        Map<String, PasswordEncoder> encoders = new LinkedHashMap<>();
        encoders.put("BCryptPasswordEncoder() (strength 10, the default)", new BCryptPasswordEncoder());
        encoders.put("BCryptPasswordEncoder(12)", new BCryptPasswordEncoder(12));
        encoders.put("BCryptPasswordEncoder(14)", new BCryptPasswordEncoder(14));
        encoders.put("Pbkdf2 defaultsForSpringSecurity_v5_8 (310,000)", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        encoders.put("Pbkdf2, 600,000 iterations (OWASP minimum)", new Pbkdf2PasswordEncoder("", 16, 600_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256));
        System.out.println("one thread; median of several runs after one warm-up");
        System.out.printf("%-52s%14s%14s%22s%n", "encoder", "encode ms", "matches ms", "logins/s per core");
        encoders.forEach((name, e) -> {
            String hash = e.encode("dosa1234");
            e.matches("dosa1234", hash);
            int n = name.contains("14") ? 3 : 9;
            double enc = medianMillis(() -> e.encode("dosa1234"), n);
            double match = medianMillis(() -> e.matches("dosa1234", hash), n);
            System.out.printf("%-52s%14.1f%14.1f%22.1f%n", name, enc, match, 1000 / match);
        });
    }

    static void upgrade() {
        Map<String, PasswordEncoder> map = new LinkedHashMap<>();
        map.put("bcrypt", new BCryptPasswordEncoder(12));
        map.put("pbkdf2@SpringSecurity_v5_8", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder("bcrypt", map);
        Map<String, String> stored = new LinkedHashMap<>();
        stored.put("{bcrypt} strength 12 (current)", encoder.encode("dosa1234"));
        stored.put("{bcrypt} strength 10", "{bcrypt}" + new BCryptPasswordEncoder(10).encode("dosa1234"));
        stored.put("{pbkdf2@SpringSecurity_v5_8}", "{pbkdf2@SpringSecurity_v5_8}"
                + Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode("dosa1234"));
        stored.put("{noop} (plain text, not mapped)", "{noop}dosa1234");
        stored.put("no {id} prefix (legacy column)", new BCryptPasswordEncoder(10).encode("dosa1234"));
        System.out.println("DelegatingPasswordEncoder: encodes {bcrypt} at 12, also maps {pbkdf2@SpringSecurity_v5_8}");
        System.out.printf("%-36s%-38s%14s%n", "stored hash", "matches the right password", "upgrade?");
        stored.forEach((name, hash) -> {
            String result;
            String upgrade;
            try {
                result = String.valueOf(encoder.matches("dosa1234", hash));
                upgrade = String.valueOf(encoder.upgradeEncoding(hash));
            } catch (IllegalArgumentException e) {
                result = "exception: " + e.getMessage().substring(0, Math.min(30, e.getMessage().length())) + "...";
                upgrade = "-";
            }
            System.out.printf("%-36s%-38s%14s%n", name, result, upgrade);
        });
    }
}
