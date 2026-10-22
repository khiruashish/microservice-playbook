package dev.foodfleet.ch07;

import dev.foodfleet.ch07.ownership.BackstageCatalog;
import dev.foodfleet.ch07.ownership.CodeOwners;
import dev.foodfleet.ch07.ownership.CodeOwners.Finding;
import dev.foodfleet.ch07.report.TopologyReport;
import dev.foodfleet.ch07.topology.FoodFleetTeams;
import dev.foodfleet.ch07.topology.Topology;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * A command-line Spring Boot app (no web server).
 *
 * <pre>
 *   mvn -q -pl chapters/ch07-team-topologies spring-boot:run                          # 2023 vs 2026 report
 *   ... -Dspring-boot.run.arguments=--codeowners=generate                             # print CODEOWNERS
 *   ... -Dspring-boot.run.arguments=--codeowners=/path/to/.github/CODEOWNERS          # check one (exit 1 on drift)
 *   ... -Dspring-boot.run.arguments=--backstage                                       # print catalog-info.yaml
 * </pre>
 */
@SpringBootApplication
public class TopologyApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(TopologyApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }

    @Bean
    ApplicationRunner run() {
        return args -> {
            int exit = execute(args);
            if (exit != 0) {
                System.exit(exit);
            }
        };
    }

    static int execute(ApplicationArguments args) {
        Topology target = FoodFleetTeams.after();
        CodeOwners codeOwners = new CodeOwners("foodfleet");
        if (args.containsOption("backstage")) {
            System.out.print(new BackstageCatalog("foodfleet").write(target));
            return 0;
        }
        if (args.containsOption("codeowners")) {
            String value = args.getOptionValues("codeowners").getFirst();
            if (value.equals("generate")) {
                System.out.print(codeOwners.generate(target));
                return 0;
            }
            CodeOwners.Parsed parsed = CodeOwners.parse(read(Path.of(value)));
            parsed.skipped().forEach(s -> System.out.println("skipped (unsupported pattern) " + s));
            List<Finding> findings = codeOwners.check(target, parsed);
            findings.forEach(f -> System.out.println("DRIFT " + f.component() + " (" + f.path() + "): " + f.problem()));
            System.out.println(findings.isEmpty() ? "CODEOWNERS matches the topology" : findings.size() + " ownership problems");
            return findings.isEmpty() ? 0 : 1;
        }
        TopologyReport report = new TopologyReport();
        System.out.println(report.render(FoodFleetTeams.before(), FoodFleetTeams.features()));
        System.out.println(report.render(target, FoodFleetTeams.features()));
        return 0;
    }

    private static List<String> read(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }
}
