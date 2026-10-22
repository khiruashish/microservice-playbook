#!/usr/bin/env python3
"""Generates FoodFleet's local stack from the platform model: compose.yaml with a profile per journey, and the
PostgreSQL init script with one role and schema per service (ADR-012).

    python3 code/foodfleet/local/generate.py        (from the repository root)
"""
import os
import sys

import yaml

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "deploy"))
sys.path.insert(0, os.path.join(HERE, "..", "model"))
from generate import required_services  # noqa: E402  (deploy/generate.py: the journey walk)

INFRA = {
    "postgres": {"image": "${POSTGRES_IMAGE:-postgres:16.13}", "mem": 512},
    "kafka": {"image": "apache/kafka:4.3.1", "mem": 768},
    "redis": {"image": "redis:7.4", "mem": 64},
    "keycloak": {"image": "quay.io/keycloak/keycloak:26.7.5", "mem": 768},
    "otel-lgtm": {"image": "grafana/otel-lgtm:0.35.0", "mem": 1024},
    "payment-provider-stub": {"image": "wiremock/wiremock:3.13.1", "mem": 256},
}
SERVICE_MEM = 384     # MiB per JVM service locally: G1 is not chosen below 2 CPUs/1.8 GB, Serial is fine here (ADR-053)


def schema(name):
    return name.replace("-", "_")


def journey_members(model, j):
    """everything a journey calls synchronously, required or optional, plus what serves its data (Kafka producers it reads)"""
    names = {j["entry"]} | ({j["via"]} if "via" in j else set())
    frontier = [j["entry"]]
    while frontier:
        n = frontier.pop()
        for c in model["calls"]:
            if c["from"] == n and not c["to"].startswith(("client:", "external:")) and c["to"] not in names:
                if n == j["entry"] and j["name"] not in c.get("journey", []):
                    continue
                names.add(c["to"])
                frontier.append(c["to"])
    return names


def main():
    model = yaml.safe_load(open(os.path.join(HERE, "..", "model", "foodfleet.yaml"), encoding="utf-8"))
    services = [s for s in model["services"] if s["kind"] != "function"]
    profiles = {s["name"]: {"full"} for s in services}
    # checkout also needs the saga's participants, which it reaches through Kafka
    extra = {"place-order": {"payment-service", "kitchen-service", "delivery-service", "promotions-service", "dispatch-optimizer", "zone-service"}}
    short = {"browse-menu": "browse", "place-order": "checkout", "track-order": "track"}
    for j in model["journeys"]:
        for n in journey_members(model, j) | extra.get(j["name"], set()):
            profiles[n].add(short[j["name"]])

    compose = {"name": "foodfleet", "services": {}, "volumes": {"pgdata": {}}}
    cs = compose["services"]
    cs["postgres"] = {"image": INFRA["postgres"]["image"], "environment": {"POSTGRES_PASSWORD": "local-only"},
                      "ports": ["5432:5432"], "volumes": ["pgdata:/var/lib/postgresql/data", "./init:/docker-entrypoint-initdb.d:ro"],
                      "healthcheck": {"test": ["CMD", "pg_isready", "-U", "postgres"], "interval": "2s", "retries": 30},
                      "mem_limit": "%dm" % INFRA["postgres"]["mem"]}
    cs["kafka"] = {"image": INFRA["kafka"]["image"], "ports": ["9092:9092"], "mem_limit": "%dm" % INFRA["kafka"]["mem"],
                   "environment": {"KAFKA_NODE_ID": "1", "KAFKA_PROCESS_ROLES": "broker,controller",
                                   "KAFKA_LISTENERS": "PLAINTEXT://:9092,CONTROLLER://:9093",
                                   "KAFKA_ADVERTISED_LISTENERS": "PLAINTEXT://kafka:9092",
                                   "KAFKA_CONTROLLER_LISTENER_NAMES": "CONTROLLER",
                                   "KAFKA_CONTROLLER_QUORUM_VOTERS": "1@kafka:9093",
                                   "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR": "1",
                                   "KAFKA_AUTO_CREATE_TOPICS_ENABLE": "false"}}
    cs["redis"] = {"image": INFRA["redis"]["image"], "ports": ["6379:6379"], "mem_limit": "%dm" % INFRA["redis"]["mem"]}
    cs["keycloak"] = {"image": INFRA["keycloak"]["image"], "command": ["start-dev", "--import-realm"], "ports": ["8180:8080"],
                      "environment": {"KC_BOOTSTRAP_ADMIN_USERNAME": "admin", "KC_BOOTSTRAP_ADMIN_PASSWORD": "local-only"},
                      "volumes": ["./realm:/opt/keycloak/data/import:ro"], "mem_limit": "%dm" % INFRA["keycloak"]["mem"]}
    cs["otel-lgtm"] = {"image": INFRA["otel-lgtm"]["image"], "ports": ["3000:3000", "4317:4317", "4318:4318"],
                       "mem_limit": "%dm" % INFRA["otel-lgtm"]["mem"]}
    cs["payment-provider-stub"] = {"image": INFRA["payment-provider-stub"]["image"], "profiles": ["checkout", "full"],
                                   "volumes": ["./stubs/payment-provider:/home/wiremock:ro"],
                                   "mem_limit": "%dm" % INFRA["payment-provider-stub"]["mem"]}
    port = 8100
    for s in services:
        stores = {x["type"] for x in s.get("stores", [])}
        env = {"SPRING_APPLICATION_NAME": s["name"],
               "JAVA_TOOL_OPTIONS": "-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:ActiveProcessorCount=1",
               "OTEL_EXPORTER_OTLP_ENDPOINT": "http://otel-lgtm:4318",
               "SPRING_KAFKA_BOOTSTRAP_SERVERS": "kafka:9092",
               "SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI": "http://keycloak:8080/realms/foodfleet"}
        deps = {"kafka": {"condition": "service_started"}}
        if "postgresql" in stores:
            db = "payments" if s["name"] == "payment-service" else "foodfleet"
            env.update({"SPRING_DATASOURCE_URL": "jdbc:postgresql://postgres:5432/%s?currentSchema=%s" % (db, schema(s["name"])),
                        "SPRING_DATASOURCE_USERNAME": schema(s["name"]), "SPRING_DATASOURCE_PASSWORD": "local-" + s["name"]})
            deps["postgres"] = {"condition": "service_healthy"}
        if "redis" in stores:
            env["SPRING_DATA_REDIS_HOST"] = "redis"
            deps["redis"] = {"condition": "service_started"}
        for c in model["calls"]:
            if c["from"] == s["name"] and not c["to"].startswith("external:"):
                group = c["to"].replace("-", "").upper()   # client groups are named without hyphens, so env vars bind
                if c["protocol"] == "gRPC":                # Spring gRPC channel (ADR-023)
                    env["SPRING_GRPC_CLIENT_CHANNEL_%s_TARGET" % group] = "static://%s:9090" % c["to"]
                else:                                      # HTTP service client group (ADR-022)
                    env["SPRING_HTTP_SERVICECLIENT_%s_BASEURL" % group] = "http://%s:8080" % c["to"]
            if c["from"] == s["name"] and c["to"].startswith("external:"):
                env["FOODFLEET_PROVIDER_BASE_URL"] = "http://payment-provider-stub:8080"
        cs[s["name"]] = {"image": "foodfleet/%s:local" % s["name"], "profiles": sorted(profiles[s["name"]]),
                         "ports": ["%d:8080" % port], "environment": env, "depends_on": deps,
                         "mem_limit": "%dm" % SERVICE_MEM}
        port += 1
    with open(os.path.join(HERE, "compose.yaml"), "w", encoding="utf-8") as f:
        f.write("# Generated by generate.py from the platform model. Infrastructure always starts; services by profile:\n"
                "#   docker compose --profile checkout up        (browse | checkout | track | full)\n"
                "# Service images are built from each service's repository with the shared Dockerfile (ADR-053), tagged :local.\n")
        yaml.safe_dump(compose, f, sort_keys=False, width=200)

    # PostgreSQL: one role and schema per service; payment-service its own database (ADR-012)
    sql = ["-- Generated by generate.py: one login role and one schema per service, nothing shared (ADR-012).",
           "CREATE DATABASE foodfleet;", "CREATE DATABASE payments;"]
    for s in services:
        if "postgresql" not in {x["type"] for x in s.get("stores", [])}:
            continue
        r, db = schema(s["name"]), ("payments" if s["name"] == "payment-service" else "foodfleet")
        sql += ["CREATE ROLE %s LOGIN PASSWORD 'local-%s';" % (r, s["name"]),
                "\\connect %s" % db,
                "REVOKE ALL ON SCHEMA public FROM PUBLIC;",
                "REVOKE CONNECT ON DATABASE %s FROM PUBLIC;" % db,
                "GRANT CONNECT ON DATABASE %s TO %s;" % (db, r),
                "CREATE SCHEMA %s AUTHORIZATION %s;" % (r, r),
                "ALTER ROLE %s SET search_path = %s;" % (r, r),
                "\\connect postgres"]
    os.makedirs(os.path.join(HERE, "init"), exist_ok=True)
    with open(os.path.join(HERE, "init", "01-schemas.sql"), "w", encoding="utf-8") as f:
        f.write("\n".join(sql) + "\n")

    mem = {}
    for p in ("browse", "checkout", "track", "full"):
        svc = [n for n in profiles if p in profiles[n]]
        infra = sum(v["mem"] for k, v in INFRA.items() if k != "payment-provider-stub" or p in ("checkout", "full"))
        mem[p] = (len(svc), len(svc) * SERVICE_MEM + infra, svc)
    rows = ["| Profile | Services | Memory limits (services + infrastructure) | Services started |", "|---|---|---|---|"]
    for p, (n, m, svc) in mem.items():
        rows.append("| `%s` | %d | %.1f GiB | %s |" % (p, n, m / 1024, ", ".join("`%s`" % x for x in svc) if p != "full" else "all"))
    with open(os.path.join(HERE, "profiles.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(rows) + "\n")
    print("\n".join(rows))
    return 0


if __name__ == "__main__":
    sys.exit(main())
