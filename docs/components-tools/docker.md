---
title: "Docker"
description: "FoodFleet's image template (ADR-053): a JDK 25 build stage, Spring Boot's layers on a jlink'd runtime on distroless, an AOT cache trained in the build, exec-form entrypoint, non-root and read-only. Measured with Docker and a local registry: 469 MB → 88 MB, a one-class change re-pushed 1.6 MB as a fat jar and 26 KB layered, first response 1.2 s → 0.5 s at 1 CPU with the AOT cache, shell-form entrypoints killed after 10 s instead of stopping in 0.17 s, and the JVM's default heap at 25% of the container limit."
progress: done
---

# Docker

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span></div>

| | |
|---|---|
| **What it is** | Building OCI images from a Dockerfile (BuildKit: multi-stage builds, cache mounts) and running them as containers with cgroup limits; registries store images by digest |
| **FoodFleet uses** | One shared Dockerfile template owned by the platform team; images tagged with the commit, deployed by digest, signed in CI |
| **Decided in** | [ADR-053](../deployment/ch53-containerization.md#8-enterprise-case-study) |
| **Learn it in** | [Chapter 53](../deployment/ch53-containerization.md), [lab 15](../code-lab/lab-15-kubernetes-helm-keda.md), [lab 17](../code-lab/lab-17-native-crac.md) |
| **Related** | [Docker Compose](docker-compose.md), [Kubernetes](kubernetes.md), [Cosign & Syft](cosign-syft.md), [Security scanners](security-scanners.md) |

## The template

```dockerfile title="Dockerfile (chapter 53)"
--8<-- "code/chapters/ch53-containerization/src/main/resources/docker/Dockerfile"
```

## Measured

**Size and what a commit costs** ([chapter 53](../deployment/ch53-containerization.md)):

| Variant | Image | Pushed | Re-pushed after a one-class change |
|---|---:|---:|---:|
| Full JDK, fat jar | **469 MB** | 139.2 MB | 1.6 MB |
| jlink (jdeps modules), fat jar | 88 MB | 30.5 MB | 1.6 MB |
| **jlink, dependency layer + app layer** | **88 MB** | 30.5 MB | **26 KB** |
| Jib CLI on the same runtime | 88 MB | 30.5 MB | 23 KB |

The JDK is most of the image; layering decides what each commit uploads (and what every node pulls on a scale-out).

**What the JVM makes of the limits** (no heap flags):

| Limits | Max heap | GC |
|---|---:|---|
| 1 GB, 2 CPUs | 247 MiB (24%) | **Serial** |
| 2 GB, 2 CPUs | 512 MiB (25%) | G1 |
| 4 GB, 1 CPU | 989 MiB | **Serial** |
| 4 GB, 1.5 CPUs | 1,024 MiB | G1 (rounds up to 2 processors) |

So: `-XX:MaxRAMPercentage=75`, `-XX:+ExitOnOutOfMemoryError`, and G1 set explicitly for small containers where pauses matter.

**Startup with the JDK 25 AOT cache** (first response, median):

| | 1 CPU | 2 CPUs |
|---|---:|---:|
| jlink runtime | 1,204–1,277 ms | 1,006–1,048 ms |
| **+ AOT cache** (+24 MB) | **485–514 ms** | 406–416 ms |

**Who receives SIGTERM:**

| Entrypoint | Stop took | Exit |
|---|---:|---|
| Exec form `["java", …]` | 165–171 ms | 143 (graceful) |
| Shell form `java …` | **10,174–10,204 ms** | **137 (SIGKILL)** |
| Script ending in `exec java …` | 157–169 ms | 143 |

**Hardening.** menu-service answered with the image's non-root user, `--read-only` (with a `/tmp` tmpfs), and `--cap-drop ALL --security-opt no-new-privileges`. It also answered as root, which is why root must be refused, not merely avoided.

## Practices

- **Build in the image** (multi-stage), with BuildKit cache mounts for `~/.m2`: reproducible, no "works on my laptop" jars.
- **Order layers** by how often they change: dependencies → loader → snapshots → application.
- **Distroless with package metadata**: an SBOM sees only what has it. Copying glibc without the Debian package database made it invisible to syft, so a scanner would never flag it.
- **Tag with the commit, deploy by digest**, rebuild weekly for base-image fixes.
- **`.dockerignore`** for `target/`, `.git`, secrets.

## Pitfalls

- **Shell-form entrypoints and wrapper scripts without `exec`**.
- **No `MaxRAMPercentage`**: three quarters of the limit unused; or `-Xmx` above the limit: OOMKilled with no Java error.
- **Secrets in build args or layers**: they stay in the image history; use BuildKit secret mounts.
- **`latest` and moving tags in deployments** ([Helm](helm.md) shows what a moved tag did).
- **An AOT cache trained on a different class path**: with `AOTMode=on` it refuses to start ([lab 17](../code-lab/lab-17-native-crac.md)); the default `auto` starts without it, slower.
