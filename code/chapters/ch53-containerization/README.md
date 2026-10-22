# Chapter 53 — Containerization

Code and measurements for [chapter 53](../../../docs/deployment/ch53-containerization.md).

| Path | What |
|---|---|
| `src/main/java/.../container/` | `ContainerLimits`: cgroup v1/v2 memory and CPU limits, and the JVM's processors, max heap and collectors, as one startup log line |
| `src/main/resources/docker/` | `Dockerfile`: the production recipe. It builds in a JDK stage, extracts Boot's layers, uses a jlink'd runtime from jdeps' modules on distroless, records an AOT cache from a training run, and has an exec-form entrypoint. `dockerignore` is the matching ignore file. |
| `src/test/java/` | 5 tests |
| `verify/` | `build-libs.sh` (dependency jars from chapters 48 and 50; registry, crane, syft and Jib CLI from GitHub; source clones), `run.sh`, `check_apis.sh` (9 checks), `tools/` (`MenuService`, `Alloc`, `Measure`) |

```bash
JAVA_HOME=/path/to/jdk-25 verify/run.sh                 # about 10 minutes
JAVA_HOME=/path/to/jdk-25 verify/run.sh jvm runtime
```

## Findings (Docker 29.4.3, JDK 25.0.4, 2 vCPUs, two runs)

**images**: menu-service has 9 dependency jars (about 8 MB). The table shows size and what a one-class change costs.

| Variant | Unpacked | Pushed | Layers | Rebuild | Re-pushed |
|---|---|---|---|---|---|
| full JDK, fat jar | 469 MB | 139.2 MB | 2 | 3.0–3.6 s | 1.6 MB |
| jlink java.se, fat jar | 119 MB | 43.5 MB | 2 | 2.8–3.0 s | 1.6 MB |
| jlink (jdeps' 7 modules), fat jar | 88 MB | 30.5 MB | 2 | 2.6–4.7 s | 1.6 MB |
| same, dependency layer + app layer | 88 MB | 30.5 MB | 4 | 1.7 s | 26 KB |
| Jib CLI 0.13 | 88.1 MB | 30.5 MB | 4 | 3.0–4.1 s | 23 KB |

**jvm**: what the JVM does with container limits.

- **Default heap:** 25% of the memory limit, or 48% at 256 MB.
- **Collector:** SerialGC unless the container has at least 2 CPUs and 2 GB; 1.5 CPUs counts as 2 processors.
- **Out of memory, in a 512 MB container:**
  - A heap that fits the limit gives a Java `OutOfMemoryError` (exit 1), or exit 3 with `ExitOnOutOfMemoryError`.
  - `-Xmx1g` with 600 MiB live: exit 137, OOMKilled, no Java output.

**startup**: median time to the first answered request.

| CPUs | jlink runtime | with the AOT cache |
|---|---|---|
| 1 | 1,204–1,277 ms | 485–514 ms |
| 2 | 1,006–1,048 ms | 406–416 ms |

The AOT cache adds 24 MB to the image.

**runtime**:

- **docker stop:** exec form or `exec java` stops in 157–171 ms with exit 143 and a graceful shutdown. Shell form or a wrapper script takes 10.2 s and ends with exit 137, with no shutdown.
- **Hardening:** menu-service answers when non-root, read-only (with or without a tmpfs), and with all capabilities dropped.
- **SBOM:** syft finds the Java runtime and the jars, but not the glibc copied in without package metadata.

**apis**: 9 of 9 found in OpenJDK 25.0.4, Spring Boot 4.1.1 and Spring Framework 7.0.9.

## Notes

- No base image can be pulled here: Docker Hub, gcr.io and Maven Central are blocked. So the lab builds its runtimes from scratch: `$JAVA_HOME` (full or jlink'd) plus the host's glibc. The production `Dockerfile` isn't built.
- Buildpacks need builder images from a registry, so they're described but not measured. Grype's vulnerability database couldn't be downloaded, so no CVE scan was run.
