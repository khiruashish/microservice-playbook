---
title: "Environment Setup"
description: "What you need to build the handbook's code and rerun its experiments: a JDK 25, Maven, git and Docker for most of it; a Kubernetes cluster, PostgreSQL 16, Redis, Go or a second JDK for some chapters; and tools/check-env.sh, which reads every chapter's requirements and tells you which ones your machine can run."
progress: done
---

# Environment Setup

<div class="badges" markdown><span class="badge badge-l1">L1 · Foundation</span><span class="badge badge-l2">L2 · Practitioner</span></div>

You don't need everything to start. A JDK 25 runs [your first microservice](first-microservice.md) with no build tool at all. Add the rest as the chapters you're reading need it.

## What each chapter needs

| Tool | Version | Needed for | Check |
|---|---|---|---|
| **JDK** | 25 (any OpenJDK build; the handbook used Eclipse Temurin 25.0.4) | everything | `java -version` |
| **Maven** | 3.9 or later, with access to Maven Central | building and testing every chapter module | `mvn -v` |
| **git** | any recent | `build-libs.sh` in chapters 23, 27 and 28–77 (pinned source checkouts) | `git --version` |
| **Docker** | Engine 24+ (or Docker Desktop, OrbStack, Colima) | Testcontainers tests (chapters 4, 15, 21, 28–34, 62) and the experiments of chapters 52–62, 64, 67–70 and 72 | `docker info` |
| **Kubernetes** | any 1.30+ cluster; the handbook used k3s 1.37 | chapters 52, 54–59, 64, 68, 70, 72 | `kubectl version` |
| **PostgreSQL** | 16, server binaries (not only `psql`) | chapters 29–34, 39, 40, 46, 52, 61, 62, 69 and 75 | `pg_config --bindir` |
| **Redis** | 7 | chapter 32 | `redis-server --version` |
| **Go** | 1.24+ | chapter 69 (franz-go, a Kafka client) | `go version` |
| **python3** | 3.10+ | chapters 29–34, 40, 41, 45, 48, 50 and 51 | `python3 --version` |
| **A second JDK** | 21, as `JDK21_HOME` | chapter 66 (virtual threads pinning on 21 against 25) | `$JDK21_HOME/bin/java -version` |
| **Linux x86-64** | – | chapters 47–49 and 51 download Linux binaries; on a Mac, run them in a Linux VM or container | `uname -m` |

The exact requirements of each chapter's experiments are at the top of its `verify/run.sh`.

## Installing

=== "macOS"

    ```bash
    # JDK 25 and Maven (SDKMAN; pick a 25 build from `sdk list java`)
    curl -s "https://get.sdkman.io" | bash
    sdk install java 25.0.4-tem
    sdk install maven

    brew install git postgresql@16 redis go python
    # Docker: Docker Desktop, OrbStack or Colima. Kubernetes: the one built into Docker Desktop or OrbStack, or kind
    ```

=== "Linux (Debian/Ubuntu)"

    ```bash
    # JDK 25 from Adoptium's repository (or SDKMAN, as on macOS)
    sudo apt install -y wget apt-transport-https gpg
    wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | sudo tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null
    echo "deb https://packages.adoptium.net/artifactory/deb $(. /etc/os-release; echo $VERSION_CODENAME) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
    sudo apt update && sudo apt install -y temurin-25-jdk maven git postgresql-16 redis-server golang python3 docker.io

    # a cluster: chapter 52's script starts single-node k3s (needs root)
    sudo code/chapters/ch52-health-checks-actuator/verify/k3s-lab.sh
    ```

=== "Windows"

    Use WSL 2 with Ubuntu and follow the Linux tab; Docker Desktop's WSL 2 backend provides Docker and, if you enable it, Kubernetes. The scripts are bash and assume a Linux file system.

Check the exact package and SDKMAN identifiers for your OS version; the commands above are a starting point, not a pinned recipe.

## Pointing the scripts at your tools

The `verify/run.sh` scripts read these variables:

| Variable | Meaning | Default |
|---|---|---|
| `JAVA_HOME` | the JDK 25 to use | required by most scripts |
| `JDK21_HOME` | a JDK 21 (chapter 66) | – |
| `PG_BIN` | PostgreSQL's server binaries | `pg_config --bindir` |
| `KUBECTL` | how to call kubectl | chapter 52's k3s |
| `LOAD_IMAGE` | a command that loads an image archive from stdin into the cluster | k3s's `ctr images import -` |

With kind, for example, set `KUBECTL=kubectl` and a `LOAD_IMAGE` that saves stdin to a file and runs `kind load image-archive` on it.

## Which chapters can your machine run?

`tools/check-env.sh` probes for each tool, reads every chapter's requirements (from the header of `verify/run.sh`, or Maven and Testcontainers for modules without one), and lists the chapters that can't run, with what's missing:

```bash
tools/check-env.sh          # chapters that can't run, and why
tools/check-env.sh --all    # every chapter
```

On the VM the handbook was written on, it reported:

```text
Tools
  jdk25     + openjdk version "25.0.4" 2026-07-21 LTS
  jdk21     - missing
  maven     + Apache Maven 3.9.11
  central   - missing
  git       + git version 2.43.0
  docker    + Docker 29.4.3
  cluster   + Server Version: v1.37.1+k3s1
  postgres  + postgres (PostgreSQL) 16.13
  redis     + Redis server v=7.0.15
  go        + go version go1.24.7 linux/amd64
  python3   + Python 3.11.15
  x86       + x86_64
Chapters
  - chapters/ch01-architecture-styles                  missing: central
  …                                                    (28 modules: missing: central)
  - chapters/ch66-latency-virtual-threads-reactive     missing: jdk21
50 of 79 can run here
```

That VM had no access to Maven Central, so its Maven modules couldn't build, while the `verify/` experiments, which fetch what they need from GitHub, ran. On a normal developer machine, with Maven Central reachable, the picture is usually reversed: everything builds, and the experiments need Docker, a cluster or PostgreSQL to be installed.

## IDE

Any IDE with Java 25 support works:

- **IntelliJ IDEA:** open `code/pom.xml` as a project and set the project SDK and language level to 25.
- **VS Code:** use the Extension Pack for Java and `java.jdt.ls.java.home` pointing at a JDK 25.

The documentation is MkDocs Material:

```bash
pip install -r requirements.txt
mkdocs serve    # http://127.0.0.1:8000
```

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `release version 25 not supported` | Maven or the IDE runs an older JDK | Set `JAVA_HOME` (and the IDE's project SDK) to a JDK 25 |
| Testcontainers: "Could not find a valid Docker environment" | Docker isn't running, or its socket isn't where expected | Start Docker; on Colima or OrbStack, set `DOCKER_HOST` to its socket |
| `pg_ctl: cannot be run as root` | PostgreSQL refuses to run as root | Run the scripts as an ordinary user; as root, they switch to the `postgres` user |
| k3s won't start in a container or restricted VM | cgroup or capability limits | `K3S_SANDBOX=1` (see the top of `k3s-lab.sh`), or use a real VM |
| `exec format error` from a downloaded binary | Not Linux x86-64 | Run that chapter in a Linux x86-64 VM or container |
