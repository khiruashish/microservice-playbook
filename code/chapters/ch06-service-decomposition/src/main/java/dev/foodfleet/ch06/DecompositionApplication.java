package dev.foodfleet.ch06;

import dev.foodfleet.ch06.decomposition.CoChangeAnalyzer;
import dev.foodfleet.ch06.decomposition.GitLogImporter;
import dev.foodfleet.ch06.report.DecompositionReport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;

/**
 * A command-line Spring Boot app (no web server) that prints the decomposition comparison.
 *
 * <pre>
 *   # bundled FoodFleet sample history
 *   mvn -q -pl chapters/ch06-service-decomposition spring-boot:run
 *
 *   # your own monolith
 *   git -C ~/work/monolith log --since=12.months --no-merges --name-only --pretty=format:'#%h' &gt; /tmp/git.log
 *   mvn -q -pl chapters/ch06-service-decomposition spring-boot:run \
 *     -Dspring-boot.run.arguments="--git-log=/tmp/git.log --mapping=/tmp/modules.txt --max-modules-per-commit=6"
 * </pre>
 */
@SpringBootApplication
public class DecompositionApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DecompositionApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }

    @Bean
    ApplicationRunner printReport() {
        return args -> System.out.println(new DecompositionReport().renderCommits(commits(args)));
    }

    static List<Set<String>> commits(ApplicationArguments args) {
        if (args.containsOption("git-log") && args.containsOption("mapping")) {
            int maxModules = args.containsOption("max-modules-per-commit")
                    ? Integer.parseInt(args.getOptionValues("max-modules-per-commit").getFirst())
                    : 6;
            GitLogImporter importer = new GitLogImporter(
                    GitLogImporter.parseRules(read(Path.of(args.getOptionValues("mapping").getFirst()))), maxModules);
            GitLogImporter.Result result = importer.importLog(read(Path.of(args.getOptionValues("git-log").getFirst())));
            System.out.printf("Imported %d of %d commits (%d mass changes and %d unmapped commits skipped)%n%n",
                    result.commits().size(), result.commitsSeen(), result.droppedNoisy(), result.droppedUnmapped());
            return result.commits();
        }
        return CoChangeAnalyzer.parseCommitLog(bundledSample());
    }

    private static List<String> read(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }

    static List<String> bundledSample() {
        try (var in = new ClassPathResource("foodfleet-commits.txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
