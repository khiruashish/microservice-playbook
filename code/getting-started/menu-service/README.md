# menu-service: your first microservice

Code for [Your First Microservice in 15 Minutes](../../../docs/getting-started/first-microservice.md). Nothing but a JDK 25.

| Path | What |
|---|---|
| `src/main/java/dev/foodfleet/menu/` | `MenuItem` (a validated record), `MenuService` (GET /restaurants/{id}/menu, GET /health, RFC 9457 errors, virtual threads, PORT from the environment) |
| `src/test/java/dev/foodfleet/menu/` | `MenuServiceTest`: five checks over HTTP, JDK only |
| `Dockerfile` | the jar on a JRE, non-root |
| `verify/run.sh` | every step of the walkthrough, timed |

```bash
java src/main/java/dev/foodfleet/menu/MenuService.java      # run from source
JAVA_HOME=/path/to/jdk-25 verify/run.sh                       # the whole walkthrough, timed (docker optional)
```

Measured (JDK 25.0.4, 2-vCPU VM): all steps 25.5 s the first time, 7.0–7.2 s after; `java -jar` to /health 0.25–0.28 s; 58 MB RSS; image content 30 MB.
