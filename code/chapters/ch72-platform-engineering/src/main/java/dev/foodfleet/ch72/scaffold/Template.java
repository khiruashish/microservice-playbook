package dev.foodfleet.ch72.scaffold;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * FoodFleet's service template: a directory of files with {@code {{name}}}, {@code {{pkg}}}, {@code {{team}}},
 * {@code {{description}}} and {@code {{version}}} placeholders (and {@code __pkg__} in paths). Rendering is
 * deliberately plain substitution: what Backstage's {@code fetch:template} or Copier do, minus their template
 * languages.
 */
public final class Template {

    public record Values(String name, String team, String description, String version) {

        public String pkg() {
            return name.replace("-", "");
        }

        String apply(String text) {
            return text.replace("{{name}}", name).replace("{{pkg}}", pkg()).replace("{{team}}", team).replace("{{description}}", description)
                    .replace("{{version}}", version).replace("__pkg__", pkg());
        }
    }

    private Template() {
    }

    /** Relative path to content, for every file in the template. */
    public static Map<String, String> render(Path template, Values v) {
        Map<String, String> out = new TreeMap<>();
        try (Stream<Path> files = Files.walk(template)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                out.put(v.apply(template.relativize(f).toString()), v.apply(Files.readString(f)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    public static void write(Map<String, String> files, Path dir) throws IOException {
        for (Map.Entry<String, String> f : files.entrySet()) {
            Path p = dir.resolve(f.getKey());
            Files.createDirectories(p.getParent());
            Files.writeString(p, f.getValue());
        }
    }

    /** Scaffolds a new service: the template's files, written to {@code dir}. */
    public static Map<String, String> scaffold(Path template, Values v, Path dir) throws IOException {
        Map<String, String> files = render(template, v);
        write(files, dir);
        return files;
    }
}
