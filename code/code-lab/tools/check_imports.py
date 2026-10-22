#!/usr/bin/env python3
"""Checks that every type the labs import exists in the source of the library version the labs build against.

The labs couldn't be compiled where the handbook was written (no Maven Central), so this is the next best check:
for each pinned repository it lists the files at the release tag (a blob-less clone: paths only, a few seconds
each), and every `import` in every lab's Java source must name a type found there, in the JDK, or in the lab itself.

    python3 code/code-lab/tools/check_imports.py [--cache DIR]      (needs git and network access to GitHub)
"""
import argparse
import glob
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
LABS = os.path.dirname(HERE)

# what Spring Boot 4.1.1's dependency management resolves to (platform/spring-boot-dependencies/build.gradle,
# gradle.properties, spring-data-bom 2026.0.1, spring-cloud-dependencies 2025.1.2)
REPOS = [
    ("spring-boot", "https://github.com/spring-projects/spring-boot", "v4.1.1"),
    ("spring-framework", "https://github.com/spring-projects/spring-framework", "v7.0.9"),
    ("spring-data-jpa", "https://github.com/spring-projects/spring-data-jpa", "4.1.1"),
    ("spring-data-commons", "https://github.com/spring-projects/spring-data-commons", "4.1.1"),
    ("spring-data-redis", "https://github.com/spring-projects/spring-data-redis", "4.1.1"),
    ("spring-kafka", "https://github.com/spring-projects/spring-kafka", "v4.1.1"),
    ("spring-security", "https://github.com/spring-projects/spring-security", "7.1.1"),
    ("spring-cloud-gateway", "https://github.com/spring-cloud/spring-cloud-gateway", "v5.0.2"),
    ("spring-cloud-commons", "https://github.com/spring-cloud/spring-cloud-commons", "v5.0.2"),
    ("spring-cloud-kubernetes", "https://github.com/spring-cloud/spring-cloud-kubernetes", "v5.0.2"),
    ("spring-cloud-vault", "https://github.com/spring-cloud/spring-cloud-vault", "v5.0.2"),
    ("spring-cloud-config", "https://github.com/spring-cloud/spring-cloud-config", "v5.0.4"),
    ("spring-cloud-netflix", "https://github.com/spring-cloud/spring-cloud-netflix", "v5.0.2"),
    ("resilience4j", "https://github.com/resilience4j/resilience4j", "v2.4.0"),
    ("testcontainers", "https://github.com/testcontainers/testcontainers-java", "2.0.5"),
    ("spring-modulith", "https://github.com/spring-projects/spring-modulith", "2.1.1"),
    ("micrometer", "https://github.com/micrometer-metrics/micrometer", "v1.17.1"),
    ("micrometer-tracing", "https://github.com/micrometer-metrics/tracing", "v1.7.1"),
    ("slf4j", "https://github.com/qos-ch/slf4j", "v_2.0.18"),                # the version chapter 50 checked under Boot 4.1.1
    ("jakarta-servlet", "https://github.com/jakartaee/servlet", "6.1.0-RELEASE"),        # Servlet 6.1, Tomcat 11 under Boot 4
    ("reactor-core", "https://github.com/reactor/reactor-core", "v3.8.7"),                # chapter 66 checked this version under Boot 4.1.1
    ("jakarta-validation", "https://github.com/jakartaee/validation", "3.1.1"),
    ("jakarta-persistence", "https://github.com/jakartaee/persistence", "3.2-3.2.0-RELEASE"),
    ("hibernate-validator", "https://github.com/hibernate/hibernate-validator", "9.1.3.Final"),
    ("assertj", "https://github.com/assertj/assertj", "assertj-build-3.27.7"),
    ("junit", "https://github.com/junit-team/junit-framework", "r6.0.3"),
    ("mockito", "https://github.com/mockito/mockito", "v5.23.0"),
    ("awaitility", "https://github.com/awaitility/awaitility", "awaitility-4.3.0"),
    ("archunit", "https://github.com/TNG/ArchUnit", "v1.5.1"),
    ("pact-jvm", "https://github.com/pact-foundation/pact-jvm", "4_7_5"),
    ("jspecify", "https://github.com/jspecify/jspecify", "v1.0.0"),
    ("jackson-databind", "https://github.com/FasterXML/jackson-databind", "jackson-databind-3.1.5"),
    ("jackson-core", "https://github.com/FasterXML/jackson-core", "jackson-core-3.1.5"),
    ("kafka", "https://github.com/apache/kafka", "4.2.1"),
    ("jackson-annotations", "https://github.com/FasterXML/jackson-annotations", "jackson-annotations-2.21"),
    ("gatling", "https://github.com/gatling/gatling", "v3.16.0"),
    ("gatling-javaapi", "https://github.com/gatling/gatling", "v3.16.0"),
]


def index(cache, name, url, tag):
    path = os.path.join(cache, name + ".paths")
    if not os.path.exists(path):
        d = os.path.join(cache, name)
        subprocess.run(["rm", "-rf", d], check=True)
        r = subprocess.run(["git", "-c", "advice.detachedHead=false", "clone", "-q", "--filter=blob:none", "--no-checkout",
                            "--depth", "1", "--branch", tag, url, d], capture_output=True, text=True)
        if r.returncode:
            print("could not index %s %s: %s" % (name, tag, r.stderr.strip()))
            return set()
        paths = subprocess.run(["git", "-C", d, "ls-tree", "-r", "--name-only", "HEAD"], capture_output=True, text=True).stdout
        open(path, "w").write(paths)
    types = set()
    for p in open(path).read().splitlines():
        m = re.search(r"/(?:java|kotlin|scala)/((?:[a-z_][\w]*/)+[A-Z]\w*)\.(?:java|kt|scala)$", p)
        if m:
            types.add(m.group(1).replace("/", "."))
    return types


# Types declared in a source file of another name (Kotlin allows several per file), so a path index can't see them.
# Each was found by reading the file at the pinned tag.
DECLARED_ELSEWHERE = {
    "au.com.dius.pact.consumer.MockServer": "pact-jvm consumer/.../MockHttpServer.kt, line 68",
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", default=os.path.join(LABS, "tools", "build", "index"))
    args = ap.parse_args()
    os.makedirs(args.cache, exist_ok=True)
    known = set()
    for name, url, tag in REPOS:
        known |= index(args.cache, name, url, tag)
    files = sorted(f for f in glob.glob(os.path.join(LABS, "lab-*", "**", "*.java"), recursive=True) if "/build/" not in f and "/target/" not in f)
    own = set()
    # the labs' own classes, and the chapter modules some labs build on (labs 10 and 11 compile chapter code)
    chapters = glob.glob(os.path.join(LABS, "..", "chapters", "*", "src", "main", "java", "**", "*.java"), recursive=True)
    for f in files + chapters:
        pkg = re.search(r"^package ([\w.]+);", open(f).read(), re.M)
        own.add("%s.%s" % (pkg.group(1) if pkg else "", os.path.basename(f)[:-5]))
    missing, checked = [], 0
    known |= set(DECLARED_ELSEWHERE)
    for f in files:
        for imp in re.findall(r"^import (?:static )?([\w.]+)(?:\.\*)?;", open(f).read(), re.M):
            if imp.startswith(("java.", "javax.", "jdk.", "com.sun.")):
                continue
            checked += 1
            parts = imp.split(".")
            # the longest prefix that is a type: handles nested types and static members
            if not any(".".join(parts[:i]) in known or ".".join(parts[:i]) in own for i in range(len(parts), 1, -1)):
                missing.append("%s: %s" % (os.path.relpath(f, LABS), imp))
    # every Spring Boot artifact a lab's pom names must be a module or starter of Boot 4.1.1
    boot_paths = open(os.path.join(args.cache, "spring-boot.paths")).read().splitlines()
    boot_artifacts = {p.split("/")[1] for p in boot_paths if p.count("/") > 1 and p.split("/")[0] in ("starter", "module", "core", "build-plugin")}
    artifacts = 0
    for pom in sorted(glob.glob(os.path.join(LABS, "lab-*", "**", "pom.xml"), recursive=True)):
        for g, a in re.findall(r"<groupId>([^<]+)</groupId>\s*<artifactId>([^<]+)</artifactId>", open(pom).read()):
            if g == "org.springframework.boot":
                artifacts += 1
                if a not in boot_artifacts:
                    missing.append("%s: artifact %s" % (os.path.relpath(pom, LABS), a))
    cloud = set()
    for name, url, tag in REPOS:
        if name.startswith("spring-cloud"):
            cloud |= {p.split("/")[0] for p in open(os.path.join(args.cache, name + ".paths")).read().splitlines() if "/" in p}
    for pom in sorted(glob.glob(os.path.join(LABS, "lab-*", "**", "pom.xml"), recursive=True)):
        for g, a in re.findall(r"<groupId>([^<]+)</groupId>\s*<artifactId>([^<]+)</artifactId>", open(pom).read()):
            if g == "org.springframework.cloud":
                artifacts += 1
                if a not in cloud:
                    missing.append("%s: artifact %s" % (os.path.relpath(pom, LABS), a))
    print("%d Spring Boot and Spring Cloud artifacts checked against their projects' modules" % artifacts)
    print("%d labs' files, %d imports checked against %d types in %d repositories" % (len(files), checked, len(known), len(REPOS)))
    for m in missing:
        print("MISSING " + m)
    print("missing: %d" % len(missing))
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
