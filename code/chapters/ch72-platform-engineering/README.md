# Chapter 72 — Platform Engineering & Golden Paths

Code and measurements for [chapter 72](../../../docs/governance-org/ch72-platform-engineering.md).

| Path | What |
|---|---|
| `src/main/java/.../scaffold/` | `Template`: renders a template directory (`{{name}}`, `{{team}}`, `{{description}}`, `{{version}}`, `__pkg__`); `Upgrade`: moves a service between template versions file by file, with `git merge-file` for files both sides changed |
| `src/main/java/.../conformance/` | `Conformance`: twelve checks, each tied to an earlier ADR; files left with conflict markers count as unreadable |
| `src/main/resources/templates/v1`, `v2` | the golden path a year apart: application, Dockerfile, manifests, VPA, catalog entry, OpenAPI, CI |
| `src/main/resources/backstage/template.yaml` | its Backstage scaffolder entry (`fetch:template`, `publish:github`, `catalog:register`) |
| `src/test/java/` | `UnitTest` (3, tag `unit`) |
| `verify/` | `build-libs.sh`, `run.sh`, `check_apis.sh` (7 checks), `tools/` (`Lab`, `RunSuites`) |

```bash
JAVA_HOME=/path/to/jdk-25 verify/run.sh   # about 2 minutes; needs docker, git and a Kubernetes cluster
```

## Findings (JDK 25.0.4, git 2.43, k3s 1.37.1; two runs, identical apart from timings)

**scaffold**: rider-ratings from v2: render 0.0 s, compile 1.1–1.2 s, image 0.4 s, load 1.8–1.9 s, deploy to 2 ready copies 2.3–4.0 s, first answer 0.7 s; **total 6.5–8.0 s**. 12 of 12 checks; 90 lines of platform files, 45 of application code.

**fleet**: twelve services, each edited by its team.

| Origin | Checks passed | Manifests accepted (server-side dry run) |
|---|---|---|
| template v2 | 46 of 48 (96%): eta-predictor added a CPU limit, menu-images dropped `ExitOnOutOfMemoryError` | 4 of 4 |
| by hand | 20 of 48 (42%): 1, 9, 10, 0 | 3 of 4: zone-planner's liveness probe had port 0, though its probe check passed |
| template v1 | 4 of 48 (8%): only memory limit = request | 4 of 4 |

**upgrade** (v1 → v2, three-way merge): updated 18, merged 2, conflicts 4, added 8; checks 4 → 40 of 48.
ratings-service 1 → 12 (endpoint and replicas merged), kitchen-display 1 → 12, coupon-service 1 → 8 (Dockerfile flag, catalog description), invoice-service 1 → 8 (CI test step, 1 GiB memory). Hand-written services have no base to merge from.

**tests**: unit 3/3. **apis**: 7 of 7 found in Backstage 1.55.3, Copier 9.19.1, git 2.56.0, VPA 1.8.0 and Kubernetes 1.37.1.
