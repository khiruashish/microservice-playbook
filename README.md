# Microservices Enterprise Handbook

MkDocs Material site + runnable Java 25 / Spring Boot 4 code, from beginner (L1) to architect (L4).
Generated chapter by chapter from `microservices-handbook-master-prompt.md`.

## Layout

```
mkdocs.yml               site config (nav block is generated, do not edit by hand)
tools/master-plan.yml    single source of truth: every page, its bands and scope
tools/handbook.py        creates stubs for planned pages, regenerates nav + roadmap
docs/                    the site (planned pages are stubs with `progress: planned`)
overrides/               landing page hero + announcement bar
code/                    Maven multi-module: chapters/, code-lab/, foodfleet/
```

## Preview the site

```bash
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
make serve          # http://127.0.0.1:8000
make build          # strict build into ./site
```

## Build and test the code (JDK 25)

```bash
cd code && mvn -B verify                 # everything
mvn -B -pl chapters/ch01-architecture-styles -am verify   # one chapter
```

## Continue the handbook

1. Paste the master prompt into a new session and say: `continue ch02` (the roadmap page shows what's done).
2. When a page is finished, set `progress: done` in its front matter.
3. Run `make sync` to refresh the nav and roadmap.

Note: GitHub only runs workflows from the repository root. In the
[microservice-playbook](https://github.com/khiruashish/microservice-playbook) repository this folder is
the root, so `.github/workflows/docs.yml` runs as is. Inside a larger repository (such as a knowledge base),
copy it to that repository's root and set `HANDBOOK_DIR` to this folder's path.

Pushing and publishing: see [PUSHING.md](PUSHING.md).
