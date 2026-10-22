import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chapter 53's experiments with Docker 29 and a local registry (distribution), on images built from scratch: no base
 * image is pulled; the runtimes are a JDK, a jlink'd Java SE and a jlink'd minimal runtime, plus the host's glibc.
 * <ul>
 *   <li>{@code images}: five ways to package menu-service; size, layers, what a one-class change re-uploads</li>
 *   <li>{@code jvm}: what the JVM makes of container memory and CPU limits; what an oversized -Xmx does</li>
 *   <li>{@code startup}: time to the first answered request, with and without the JDK 25 AOT cache, at 1 and 2 CPUs</li>
 *   <li>{@code runtime}: shell-form vs exec-form entrypoints on docker stop; non-root, read-only, no capabilities</li>
 * </ul>
 * System properties: c53.work (build contexts), c53.jdk (a JDK 25), c53.app (compiled classes: main, tools),
 * c53.libs (dependency classes, one directory per jar), c53.bin (registry, crane, jib, syft).
 */
public final class Measure {

    static final Path WORK = Path.of(System.getProperty("c53.work", "/tmp/c53"));
    static final Path JDK = Path.of(System.getProperty("c53.jdk"));
    static final Path APP = Path.of(System.getProperty("c53.app"));
    static final Path LIBS = Path.of(System.getProperty("c53.libs"));
    static final Path BIN = Path.of(System.getProperty("c53.bin"));
    static final String REG = "127.0.0.1:5053";
    static final HttpClient HTTP = HttpClient.newBuilder().proxy(ProxySelector.of(null)).connectTimeout(Duration.ofMillis(200)).build();

    public static void main(String[] args) throws Exception {
        Files.createDirectories(WORK);
        bases();
        for (String mode : args) {
            switch (mode) {
                case "images" -> images();
                case "jvm" -> jvm();
                case "startup" -> startup();
                case "runtime" -> runtime();
                default -> throw new IllegalArgumentException(mode);
            }
        }
    }

    // ===================================================================================================== helpers

    record Out(int code, String text) {
    }

    static Out run(Path dir, String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        if (dir != null) {
            pb.directory(dir.toFile());
        }
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        Process p = pb.start();
        String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Out(p.waitFor(), text);
    }

    static String must(Path dir, String... cmd) throws IOException, InterruptedException {
        Out o = run(dir, cmd);
        if (o.code() != 0) {
            throw new IllegalStateException(String.join(" ", cmd) + " failed:\n" + o.text());
        }
        return o.text();
    }

    static void copyTree(Path from, Path to) throws IOException, InterruptedException {
        Files.createDirectories(to);
        must(null, "cp", "-a", from + "/.", to.toString());
    }

    /** The JVM's shared libraries from the host, and a passwd with a non-root user, under {@code root}. */
    static void os(Path root) throws IOException, InterruptedException {
        Files.createDirectories(root.resolve("lib/x86_64-linux-gnu"));
        Files.createDirectories(root.resolve("lib64"));
        Files.createDirectories(root.resolve("etc"));
        Files.createDirectories(root.resolve("tmp"));
        for (String l : List.of("libc.so.6", "libdl.so.2", "libm.so.6", "libpthread.so.0", "librt.so.1", "libz.so.1")) {
            must(null, "cp", "-L", "/lib/x86_64-linux-gnu/" + l, root.resolve("lib/x86_64-linux-gnu").toString());
        }
        must(null, "cp", "-L", "/lib64/ld-linux-x86-64.so.2", root.resolve("lib64").toString());
        Files.writeString(root.resolve("etc/passwd"), "root:x:0:0:root:/:/sbin/nologin\napp:x:10001:10001:app:/app:/sbin/nologin\n");
        Files.writeString(root.resolve("etc/group"), "root:x:0:\napp:x:10001:\n");
        must(null, "chmod", "1777", root.resolve("tmp").toString());
    }

    /** The unpacked size Docker reports (e.g. "30.5MB"). */
    static String imageSize(String image) throws Exception {
        return must(null, "docker", "image", "ls", "--format", "{{.Size}}", image).trim().replaceAll("([0-9.]+)([kMG]?B)", "$1 $2");
    }

    // ======================================================================================================= bases

    /** Three runtimes on empty images: the whole JDK, jlink'd java.se, and jlink'd with the modules jdeps found. */
    static void bases() throws Exception {
        if (run(null, "docker", "image", "inspect", "c53/jre-min:25").code() == 0) {
            return;
        }
        Path b = WORK.resolve("bases");
        must(null, "rm", "-rf", b.toString());
        // the JDK as it is
        os(b.resolve("jdk/rootfs"));
        copyTree(JDK, b.resolve("jdk/rootfs/opt/java"));
        // jlink: all of Java SE
        os(b.resolve("jre/rootfs"));
        must(null, JDK.resolve("bin/jlink").toString(), "--add-modules", "java.se,jdk.httpserver,jdk.management,jdk.crypto.ec,jdk.jcmd,jdk.unsupported",
                "--strip-debug", "--no-header-files", "--no-man-pages", "--compress=zip-6", "--output", b.resolve("jre/rootfs/opt/java").toString());
        // jlink: only what jdeps says menu-service and its libraries use
        String modules = jdepsModules();
        os(b.resolve("min/rootfs"));
        must(null, JDK.resolve("bin/jlink").toString(), "--add-modules", modules, "--strip-debug", "--no-header-files", "--no-man-pages",
                "--compress=zip-6", "--output", b.resolve("min/rootfs/opt/java").toString());
        Files.writeString(b.resolve("modules.txt"), modules);
        for (String[] v : new String[][] {{"jdk", "c53/jdk:25"}, {"jre", "c53/jre:25"}, {"min", "c53/jre-min:25"}}) {
            Files.writeString(b.resolve(v[0]).resolve("Dockerfile"), "FROM scratch\nCOPY rootfs/ /\nENV PATH=/opt/java/bin JAVA_HOME=/opt/java\nUSER 10001\n");
            must(null, "docker", "build", "-q", "-t", v[1], b.resolve(v[0]).toString());
        }
    }

    static String jdepsModules() throws Exception {
        List<String> cmd = new ArrayList<>(List.of(JDK.resolve("bin/jdeps").toString(), "--ignore-missing-deps", "--print-module-deps",
                "--multi-release", "25", "--class-path", libJars().stream().map(Path::toString).reduce((a, c) -> a + ":" + c).orElse("")));
        cmd.add(APP.resolve("main").toString());
        cmd.add(APP.resolve("tools").toString());
        Set<String> mods = new LinkedHashSet<>(Arrays.asList(must(null, cmd.toArray(String[]::new)).trim().split(",")));
        mods.add("jdk.crypto.ec");       // TLS to other services
        mods.add("jdk.jcmd");            // thread and heap dumps in production
        return String.join(",", mods);
    }

    /** One jar per library directory (built once). */
    static List<Path> libJars() throws Exception {
        Path dir = WORK.resolve("lib");
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            try (var ds = Files.list(LIBS)) {
                for (Path lib : ds.sorted().toList()) {
                    must(null, JDK.resolve("bin/jar").toString(), "--create", "--file", dir.resolve(lib.getFileName() + ".jar").toString(), "-C", lib.toString(), ".");
                }
            }
        }
        try (var ds = Files.list(dir)) {
            return ds.sorted().toList();
        }
    }

    /** menu-service's own jar, with its dependencies on the manifest Class-Path (lib/*.jar). */
    static Path appJar(Path out, String version) throws Exception {
        Path classes = WORK.resolve("app-classes-" + version);
        must(null, "rm", "-rf", classes.toString());
        copyTree(APP.resolve("main"), classes);
        copyTree(APP.resolve("tools"), classes);
        Files.writeString(classes.resolve("logback.xml"), """
                <configuration>
                  <appender name="OUT" class="ch.qos.logback.core.ConsoleAppender">
                    <encoder><pattern>%d{HH:mm:ss.SSS} %-5level %logger - %msg%n</pattern></encoder>
                  </appender>
                  <root level="INFO"><appender-ref ref="OUT"/></root>
                </configuration>
                """);
        // the one-line change between builds: a constant in one of the application's classes
        Path src = WORK.resolve("BuildInfo-" + version + "/BuildInfo.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, "public final class BuildInfo { public static final String VERSION = \"1.4." + version + "\"; }\n");
        must(null, JDK.resolve("bin/javac").toString(), "-d", classes.toString(), src.toString());
        StringBuilder cp = new StringBuilder();
        for (Path j : libJars()) {
            cp.append(cp.isEmpty() ? "" : " ").append("lib/").append(j.getFileName());
        }
        Path manifest = WORK.resolve("MANIFEST-" + version + ".MF");
        Files.writeString(manifest, "Main-Class: MenuService\nClass-Path: " + cp + "\n");
        Files.createDirectories(out.getParent());
        must(null, JDK.resolve("bin/jar").toString(), "--create", "--file", out.toString(), "--manifest", manifest.toString(), "-C", classes.toString(), ".");
        return out;
    }

    /** A fat jar: menu-service and every dependency's classes in one file (what a shaded or uber jar is). */
    static Path fatJar(Path out, String version) throws Exception {
        Path classes = WORK.resolve("fat-classes-" + version);
        must(null, "rm", "-rf", classes.toString());
        Files.createDirectories(classes);
        try (var ds = Files.list(LIBS)) {
            for (Path lib : ds.sorted().toList()) {
                copyTree(lib, classes);
            }
        }
        Path thin = appJar(WORK.resolve("thin-" + version + ".jar"), version);
        must(classes, JDK.resolve("bin/jar").toString(), "--extract", "--file", thin.toString());
        Files.delete(classes.resolve("META-INF/MANIFEST.MF"));
        Path manifest = WORK.resolve("FAT-MANIFEST.MF");
        Files.writeString(manifest, "Main-Class: MenuService\n");
        must(null, JDK.resolve("bin/jar").toString(), "--create", "--file", out.toString(), "--manifest", manifest.toString(), "-C", classes.toString(), ".");
        return out;
    }

    // ====================================================================================================== images

    record Variant(String label, String tag, String base, boolean layered, boolean jib) {
    }

    static void images() throws Exception {
        Process registry = startRegistry();
        try {
            List<Variant> variants = List.of(
                    new Variant("JDK, fat jar in one layer", "jdk-fat", "c53/jdk:25", false, false),
                    new Variant("jlink java.se, fat jar", "jre-fat", "c53/jre:25", false, false),
                    new Variant("jlink (jdeps modules), fat jar", "min-fat", "c53/jre-min:25", false, false),
                    new Variant("jlink (jdeps modules), dependencies layer + app layer", "min-layered", "c53/jre-min:25", true, false),
                    new Variant("Jib CLI 0.13 on jlink (jdeps modules)", "jib", "c53/jre-min:25", true, true));
            System.out.println("menu-service: " + libJars().size() + " dependency jars (Micrometer, Prometheus client, Logback, SLF4J, ...), "
                    + "modules from jdeps: " + Files.readString(WORK.resolve("bases/modules.txt")));
            System.out.println("variant                                                 image size   pushed (compressed)   layers   one class changed: rebuild   re-pushed");
            for (Variant v : variants) {
                String[] r1 = build(v, "1");
                String repo = REG + "/menu-service";
                Map<String, Long> before = layers(repo + ":" + v.tag() + "-1");
                long t = System.nanoTime();
                build(v, "2");
                long rebuildMs = (System.nanoTime() - t) / 1_000_000;
                Map<String, Long> after = layers(repo + ":" + v.tag() + "-2");
                long reupload = after.entrySet().stream().filter(e -> !before.containsKey(e.getKey())).mapToLong(Map.Entry::getValue).sum();
                long compressed = before.values().stream().mapToLong(Long::longValue).sum();
                System.out.printf("%-55s %10s   %19s   %6d   %25s   %9s%n", v.label(), r1[0], mb(compressed), before.size(), rebuildMs + " ms", mb(reupload));
            }
        } finally {
            registry.destroy();
            registry.waitFor();
        }
        System.out.println();
    }

    /** Builds and pushes one variant at one version; returns {image size}. */
    static String[] build(Variant v, String version) throws Exception {
        String image = REG + "/menu-service:" + v.tag() + "-" + version;
        Path ctx = WORK.resolve("ctx-" + v.tag());
        if (v.jib()) {
            // Jib reads the jar and its Class-Path, and builds dependency, resource and class layers itself
            Path jar = appJar(ctx.resolve("menu-service.jar"), version);
            copyLibs(ctx);
            must(null, "docker", "tag", v.base(), REG + "/jre-min:25");
            must(null, "docker", "push", "-q", REG + "/jre-min:25");
            must(ctx, BIN.resolve("jib/bin/jib").toString(), "jar", "--target", image, "--from", REG + "/jre-min:25",
                    "--allow-insecure-registries", "--jvm-flags=-XX:MaxRAMPercentage=75", jar.toString());
            must(null, "docker", "pull", "-q", image);
            return new String[] {imageSize(image)};
        }
        must(null, "rm", "-rf", ctx.toString());
        Files.createDirectories(ctx);
        String dockerfile;
        if (v.layered()) {
            appJar(ctx.resolve("app/menu-service.jar"), version);
            copyLibs(ctx.resolve("app"));
            dockerfile = """
                    FROM %s
                    WORKDIR /app
                    COPY app/lib/ /app/lib/
                    COPY app/menu-service.jar /app/
                    ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/menu-service.jar"]
                    """.formatted(v.base());
        } else {
            fatJar(ctx.resolve("menu-service.jar"), version);
            dockerfile = """
                    FROM %s
                    COPY menu-service.jar /app/menu-service.jar
                    ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/menu-service.jar"]
                    """.formatted(v.base());
        }
        Files.writeString(ctx.resolve("Dockerfile"), dockerfile);
        must(null, "docker", "build", "-q", "-t", image, ctx.toString());
        must(null, "docker", "push", "-q", image);
        return new String[] {imageSize(image)};
    }

    static void copyLibs(Path dir) throws Exception {
        Files.createDirectories(dir.resolve("lib"));
        for (Path j : libJars()) {
            Files.copy(j, dir.resolve("lib").resolve(j.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // identical timestamps, so an unchanged jar is an identical layer file
            Files.setLastModifiedTime(dir.resolve("lib").resolve(j.getFileName()), java.nio.file.attribute.FileTime.fromMillis(0));
        }
    }

    /** Layer digest → compressed size, from the registry's manifest. */
    static Map<String, Long> layers(String image) throws Exception {
        String manifest = must(null, BIN.resolve("crane").toString(), "manifest", "--insecure", "--platform", "linux/amd64", image);
        Map<String, Long> m = new LinkedHashMap<>();
        int at = manifest.indexOf("\"layers\"");
        Matcher mm = Pattern.compile("\"size\"\\s*:\\s*(\\d+)\\s*,\\s*\"digest\"\\s*:\\s*\"([^\"]+)\"|\"digest\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"size\"\\s*:\\s*(\\d+)")
                .matcher(manifest.substring(at));
        while (mm.find()) {
            if (mm.group(2) != null) {
                m.put(mm.group(2), Long.parseLong(mm.group(1)));
            } else {
                m.put(mm.group(3), Long.parseLong(mm.group(4)));
            }
        }
        return m;
    }

    static Process startRegistry() throws Exception {
        Path cfg = WORK.resolve("registry.yml");
        Files.writeString(cfg, """
                version: 0.1
                storage: {filesystem: {rootdirectory: %s}}
                http: {addr: %s}
                log: {level: error}
                """.formatted(WORK.resolve("registry-data"), REG));
        must(null, "rm", "-rf", WORK.resolve("registry-data").toString());
        Process p = new ProcessBuilder(BIN.resolve("registry").toString(), "serve", cfg.toString()).redirectErrorStream(true)
                .redirectOutput(WORK.resolve("registry.log").toFile()).start();
        for (int i = 0; i < 100; i++) {
            try {
                HTTP.send(HttpRequest.newBuilder(URI.create("http://" + REG + "/v2/")).build(), HttpResponse.BodyHandlers.discarding());
                return p;
            } catch (IOException e) {
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("registry didn't start");
    }

    static String mb(long bytes) {
        return bytes < 1024 * 1024 ? String.format("%.0f KB", bytes / 1024.0) : String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }

    // ========================================================================================================= jvm

    static void jvm() throws Exception {
        Path ctx = WORK.resolve("ctx-alloc");
        must(null, "rm", "-rf", ctx.toString());
        copyTree(APP.resolve("main"), ctx.resolve("classes"));
        copyTree(APP.resolve("tools"), ctx.resolve("classes"));
        Files.writeString(ctx.resolve("Dockerfile"), "FROM c53/jre-min:25\nCOPY classes /app/classes\n");
        must(null, "docker", "build", "-q", "-t", "c53/alloc", ctx.toString());
        System.out.println("what the JVM (25.0.4, no heap flags) makes of the container's limits");
        System.out.println("docker run                         what the JVM reports");
        for (String[] limits : new String[][] {{"--memory=256m", "--cpus=2"}, {"--memory=512m", "--cpus=2"}, {"--memory=1g", "--cpus=2"},
                {"--memory=2g", "--cpus=2"}, {"--memory=4g", "--cpus=2"}, {"--memory=1g", "--cpus=0.5"}, {"--memory=1g", "--cpus=1"},
                {"--memory=4g", "--cpus=1"}, {"--memory=4g", "--cpus=1.5"}}) {
            String out = must(null, "docker", "run", "--rm", limits[0], limits[1], "c53/alloc", "java", "-cp", "/app/classes", "Alloc", "0").trim()
                    .lines().findFirst().orElse("");
            System.out.printf("%-34s %s%n", limits[0] + " " + limits[1], out.substring(out.indexOf("jvm:")));
        }
        System.out.println();
        System.out.println("--memory=512m: the program keeps N MiB of live data (docker exit code, OOMKilled, last line)");
        for (String[] row : new String[][] {{"400"}, {"400", "-XX:MaxRAMPercentage=75"}, {"400", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError"},
                {"300", "-XX:MaxRAMPercentage=75"}, {"600", "-Xmx1g"}}) {
            String[] flags = Arrays.copyOfRange(row, 1, row.length);
            String name = "c53-oom-" + System.nanoTime();
            List<String> cmd = new ArrayList<>(List.of("docker", "run", "--name", name, "--memory=512m", "--memory-swap=512m", "c53/alloc", "java"));
            cmd.addAll(Arrays.asList(flags));
            cmd.addAll(List.of("-cp", "/app/classes", "Alloc", row[0]));
            Out o = run(null, cmd.toArray(String[]::new));
            String oom = must(null, "docker", "inspect", name, "--format", "{{.State.OOMKilled}}").trim();
            run(null, "docker", "rm", name);
            String[] lines = o.text().trim().split("\n");
            String last = lines[lines.length - 1];
            if (last.length() > 70) {
                last = last.substring(0, 70) + "...";
            }
            System.out.printf("%4s MiB  %-50s exit %3d  OOMKilled %-5s  %s%n", row[0], flags.length == 0 ? "(no flags: 25% of the limit)" : String.join(" ", flags),
                    o.code(), oom, last.trim());
        }
        System.out.println();
    }

    // ===================================================================================================== startup

    static void startup() throws Exception {
        Path ctx = menuImage();
        // JDK 25: a training run records the AOT cache (classes loaded and linked, method profiles) in the image
        Files.writeString(ctx.resolve("Dockerfile.aot"), """
                FROM c53/menu
                USER root
                RUN ["java", "-XX:AOTCacheOutput=/app/menu-service.aot", "-jar", "/app/menu-service.jar", "--train"]
                USER 10001
                ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:AOTCache=/app/menu-service.aot", "-jar", "/app/menu-service.jar"]
                """);
        must(ctx, "docker", "build", "-q", "-f", "Dockerfile.aot", "-t", "c53/menu-aot", ".");
        System.out.println("menu-service started 7 times per configuration; time from docker start to the first answered GET /menu (median, min-max)");
        System.out.println("image                               cpus   first response             the service's own \"started in\"");
        for (String image : List.of("c53/menu", "c53/menu-aot")) {
            for (String cpus : List.of("1", "2")) {
                List<Long> ttfr = new ArrayList<>();
                List<Long> self = new ArrayList<>();
                for (int i = 0; i < 7; i++) {
                    long[] r = timeToFirstResponse(image, cpus);
                    ttfr.add(r[0]);
                    self.add(r[1]);
                }
                Collections.sort(ttfr);
                Collections.sort(self);
                System.out.printf("%-35s %4s   %5d ms (%d-%d)          %5d ms%n", image.equals("c53/menu") ? "jlink runtime" : "jlink runtime + AOT cache",
                        cpus, ttfr.get(3), ttfr.getFirst(), ttfr.getLast(), self.get(3));
            }
        }
        System.out.printf("AOT cache in the image: %s (image %s vs %s)%n", aotSize(), imageSize("c53/menu-aot"), imageSize("c53/menu"));
        System.out.println();
    }

    /** menu-service, layered, on the jlink'd runtime: the image the startup and runtime experiments use. */
    static Path menuImage() throws Exception {
        Path ctx = WORK.resolve("ctx-startup");
        must(null, "rm", "-rf", ctx.toString());
        appJar(ctx.resolve("app/menu-service.jar"), "1");
        copyLibs(ctx.resolve("app"));
        Files.writeString(ctx.resolve("Dockerfile"), """
                FROM c53/jre-min:25
                WORKDIR /app
                COPY app/lib/ /app/lib/
                COPY app/menu-service.jar /app/
                ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/menu-service.jar"]
                """);
        must(null, "docker", "build", "-q", "-t", "c53/menu", ctx.toString());
        return ctx;
    }

    static String aotSize() throws Exception {
        String out = must(null, "docker", "history", "--no-trunc", "--format", "{{.Size}}\t{{.CreatedBy}}", "c53/menu-aot");
        for (String l : out.split("\n")) {
            if (l.contains("AOTCacheOutput")) {
                return l.substring(0, l.indexOf('\t'));
            }
        }
        return "?";
    }

    /** {ms from docker start to the first 200 from /menu, the service's own "started in"}. */
    static long[] timeToFirstResponse(String image, String cpus) throws Exception {
        String name = "c53-start-" + System.nanoTime();
        must(null, "docker", "create", "--name", name, "--cpus=" + cpus, "--memory=512m", image);
        long t = System.nanoTime();
        must(null, "docker", "start", name);
        String ip = must(null, "docker", "inspect", name, "--format", "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}").trim();
        long first = -1;
        while (System.nanoTime() - t < 30_000_000_000L) {
            try {
                HttpResponse<Void> r = HTTP.send(HttpRequest.newBuilder(URI.create("http://" + ip + ":8080/menu")).timeout(Duration.ofMillis(500)).build(),
                        HttpResponse.BodyHandlers.discarding());
                if (r.statusCode() == 200) {
                    first = (System.nanoTime() - t) / 1_000_000;
                    break;
                }
            } catch (IOException e) {
                Thread.sleep(5);
            }
        }
        String logs = must(null, "docker", "logs", name);
        run(null, "docker", "rm", "-f", name);
        Matcher m = Pattern.compile("started in (\\d+) ms").matcher(logs);
        return new long[] {first, m.find() ? Long.parseLong(m.group(1)) : -1};
    }

    // ===================================================================================================== runtime

    static void runtime() throws Exception {
        menuImage();
        // a shell for the shell-form entrypoints: the host's dash (Ubuntu 24.04's /bin/sh) and the libc it needs
        Path ctx = WORK.resolve("ctx-shell");
        must(null, "rm", "-rf", ctx.toString());
        Files.createDirectories(ctx.resolve("rootfs/bin"));
        must(null, "cp", "-L", "/bin/dash", ctx.resolve("rootfs/bin/sh").toString());
        Files.writeString(ctx.resolve("rootfs/start.sh"), "#!/bin/sh\necho starting menu-service\njava -XX:MaxRAMPercentage=75 -jar /app/menu-service.jar\necho menu-service exited\n");
        Files.writeString(ctx.resolve("rootfs/start-exec.sh"), "#!/bin/sh\necho starting menu-service\nexec java -XX:MaxRAMPercentage=75 -jar /app/menu-service.jar\n");
        must(null, "chmod", "755", ctx.resolve("rootfs/start.sh").toString(), ctx.resolve("rootfs/start-exec.sh").toString());
        for (String[] v : new String[][] {{"c53/menu-shell", "ENTRYPOINT java -XX:MaxRAMPercentage=75 -jar /app/menu-service.jar"},
                {"c53/menu-script", "ENTRYPOINT [\"/start.sh\"]"}, {"c53/menu-script-exec", "ENTRYPOINT [\"/start-exec.sh\"]"}}) {
            Files.writeString(ctx.resolve("Dockerfile"), "FROM c53/menu\nCOPY rootfs/ /\n" + v[1] + "\n");
            must(null, "docker", "build", "-q", "-t", v[0], ctx.toString());
        }
        System.out.println("docker stop (SIGTERM, then SIGKILL after 10 s): who receives the signal");
        System.out.println("ENTRYPOINT                             processes              stop took   exit   graceful shutdown logged");
        for (String[] v : new String[][] {{"exec form [\"java\", ...]", "c53/menu"}, {"shell form: java ... (/bin/sh -c)", "c53/menu-shell"},
                {"script: echo; java ...; echo", "c53/menu-script"}, {"script: echo; exec java ...", "c53/menu-script-exec"}}) {
            String name = "c53-sig-" + System.nanoTime();
            must(null, "docker", "run", "-d", "--name", name, v[1]);
            Thread.sleep(3000);
            String comm = run(null, "docker", "top", name, "-o", "pid,args").text();
            long t = System.nanoTime();
            must(null, "docker", "stop", "-t", "10", name);
            long ms = (System.nanoTime() - t) / 1_000_000;
            String code = must(null, "docker", "inspect", name, "--format", "{{.State.ExitCode}}").trim();
            String logs = must(null, "docker", "logs", name);
            run(null, "docker", "rm", name);
            boolean shell = comm.lines().skip(1).anyMatch(l -> l.contains("/bin/sh") || l.contains("start"));
            System.out.printf("%-38s %-22s %6d ms   %4s   %s%n", v[0], shell ? "shell, java as its child" : "java", ms, code,
                    logs.contains("SIGTERM received") ? "yes" : "no");
        }
        System.out.println();
        System.out.println("hardening: does menu-service start and answer?");
        for (String[] v : new String[][] {
                {"defaults (user 10001 from the image)"},
                {"--read-only", "--read-only"},
                {"--read-only --tmpfs /tmp", "--read-only", "--tmpfs", "/tmp"},
                {"--cap-drop ALL --security-opt no-new-privileges", "--cap-drop", "ALL", "--security-opt", "no-new-privileges"},
                {"--user 0 (root)", "--user", "0"}}) {
            String name = "c53-hard-" + System.nanoTime();
            List<String> cmd = new ArrayList<>(List.of("docker", "run", "-d", "--name", name));
            cmd.addAll(Arrays.asList(v).subList(1, v.length));
            cmd.add("c53/menu");
            must(null, cmd.toArray(String[]::new));
            Thread.sleep(3000);
            String ip = must(null, "docker", "inspect", name, "--format", "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}").trim();
            String status;
            try {
                status = String.valueOf(HTTP.send(HttpRequest.newBuilder(URI.create("http://" + ip + ":8080/menu")).timeout(Duration.ofSeconds(2)).build(),
                        HttpResponse.BodyHandlers.discarding()).statusCode());
            } catch (IOException e) {
                status = "no answer";
            }
            String logs = must(null, "docker", "logs", name);
            String warn = logs.lines().filter(l -> l.contains("warning") || l.contains("Warning") || l.contains("WARN")).findFirst().orElse("");
            String user = must(null, "docker", "exec", name, "java", "-XshowSettings:properties", "-version").lines()
                    .filter(l -> l.trim().startsWith("user.name")).findFirst().orElse("?").trim();
            run(null, "docker", "rm", "-f", name);
            System.out.printf("%-52s GET /menu %-9s %-22s %s%n", v[0], status, user, warn.length() > 90 ? warn.substring(0, 90) + "..." : warn);
        }
        System.out.println();
        sbom();
    }

    /** What syft finds in each image: packages a scanner would match against vulnerability databases. */
    static void sbom() throws Exception {
        System.out.println("syft 1.54.1: what an SBOM of each image lists (the input to a CVE scan)");
        for (String image : List.of("c53/jdk:25", "c53/jre:25", "c53/jre-min:25", "c53/menu")) {
            String json = must(null, BIN.resolve("syft").toString(), "scan", "docker:" + image, "-o", "json", "-q");
            Map<String, Integer> byType = new LinkedHashMap<>();
            Matcher m = Pattern.compile("\"type\"\\s*:\\s*\"([a-z-]+)\"").matcher(json.substring(0, json.indexOf("\"artifactRelationships\"")));
            while (m.find()) {
                byType.merge(m.group(1), 1, Integer::sum);
            }
            long files = Long.parseLong(must(null, "bash", "-c", "docker export $(docker create " + image + " x) | tar t | grep -vc '/$'").trim());
            System.out.printf("   %-18s %8s   %5d files   packages by type: %s%n", image, imageSize(image), files, byType);
        }
        run(null, "bash", "-c", "docker ps -aq --filter status=created | xargs -r docker rm >/dev/null");
        System.out.println();
    }
}
