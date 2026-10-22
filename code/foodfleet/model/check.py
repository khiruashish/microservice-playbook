#!/usr/bin/env python3
"""Validates FoodFleet's platform model (foodfleet.yaml) against itself and against the handbook's ADRs, computes what
the model implies for each user journey, and writes the Markdown and Mermaid fragments the FoodFleet pages include.

    python3 code/foodfleet/model/check.py        (from the repository root; exit code 1 on any error)
"""
import glob
import os
import re
import sys

import yaml

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
OUT = os.path.join(HERE, "generated")


def adrs_in_docs():
    found = {}
    for f in glob.glob(os.path.join(ROOT, "docs", "*", "ch[0-9][0-9]-*.md")):
        for m in re.finditer(r'!!! decision "ADR-(\d{3}): ([^"]*)"', open(f, encoding="utf-8").read()):
            found[int(m.group(1))] = (m.group(2), os.path.relpath(f, os.path.join(ROOT, "docs")))
    return found


def main():
    model = yaml.safe_load(open(os.path.join(HERE, "foodfleet.yaml"), encoding="utf-8"))
    adrs = adrs_in_docs()
    ADRS.update(adrs)
    errors = []
    # an unquoted comma inside a YAML flow mapping silently splits a value into extra keys with no value
    allowed = {"teams": {"name", "type", "people", "ch"},
               "services": {"name", "namespace", "kind", "team", "assumed_owner", "aliases", "context", "purpose", "tech", "stores", "sidecars", "adrs"},
               "platform": {"name", "team", "purpose", "adrs"},
               "calls": {"from", "to", "protocol", "adr", "ch", "journey", "fallback", "assumed", "via"},
               "topics": {"name", "aliases", "kind", "key", "partitions", "producer", "consumers", "adr", "ch", "assumed"},
               "journeys": {"name", "via", "entry", "priority", "target", "slo", "ch"}}
    for section, keys in allowed.items():
        for item in model[section]:
            for k, v in item.items():
                if k not in keys or v is None:
                    errors.append("%s %s: unexpected key %r (an unquoted comma?)" % (section, item.get("name", item.get("from")), k))
    for s in model["services"]:
        for x in s.get("stores", []):
            if set(x) != {"type", "use"}:
                errors.append("%s: store %r (an unquoted comma?)" % (s["name"], x))
    teams = {t["name"]: t for t in model["teams"]}
    services = {s["name"]: s for s in model["services"]}
    platform = {p["name"]: p for p in model["platform"]}
    names = {}
    for s in model["services"]:
        for n in [s["name"]] + s.get("aliases", []):
            if n in names:
                errors.append("name %s used by %s and %s" % (n, names[n], s["name"]))
            names[n] = s["name"]
        if s["team"] not in teams:
            errors.append("%s: unknown team %s" % (s["name"], s["team"]))
    referenced = set()
    for item in model["services"] + model["platform"]:
        for a in item.get("adrs", []):
            referenced.add(a)
    for c in model["calls"] + model["topics"]:
        if "adr" in c:
            referenced.add(c["adr"])
    for a in sorted(referenced):
        if a not in adrs:
            errors.append("ADR-%03d is referenced but not in the handbook" % a)

    def known(endpoint):
        return endpoint.split(":")[0] in ("client", "external") or endpoint in services or endpoint in platform

    for c in model["calls"]:
        for end in (c["from"], c["to"]):
            if not known(end):
                errors.append("call %s -> %s: unknown %s" % (c["from"], c["to"], end))
        if "adr" not in c and "ch" not in c:
            errors.append("call %s -> %s has no source" % (c["from"], c["to"]))
    for t in model["topics"]:
        for end in [t["producer"]] + t["consumers"]:
            if end not in services:
                errors.append("topic %s: unknown service %s" % (t["name"], end))

    # cycles in the synchronous call graph
    graph = {}
    for c in model["calls"]:
        graph.setdefault(c["from"], []).append(c["to"])
    state = {}

    def visit(n, path):
        state[n] = 1
        for m in graph.get(n, []):
            if state.get(m) == 1:
                errors.append("synchronous cycle: " + " -> ".join(path + [m]))
            elif m not in state:
                visit(m, path + [m])
        state[n] = 2

    for n in list(graph):
        if n not in state:
            visit(n, [n])

    # journeys: which services each touches synchronously, how deep, and what availability that implies
    journeys = []
    for j in model["journeys"]:
        hard, soft, depth = set(), set(), {}

        def walk(node, d, required, seen):
            for c in model["calls"]:
                if c["from"] != node or c["to"] in seen or c["to"].startswith(("client:", "external:")):
                    continue
                on_path = node == j["entry"] and j["name"] in c.get("journey", []) or node != j["entry"]
                if not on_path:
                    continue
                if node != j["entry"] and node not in hard | soft:
                    continue
                req = required and not c.get("fallback", "").startswith(("hide", "last known", "place the order without", "positions without"))
                (hard if req else soft).add(c["to"])
                depth[c["to"]] = max(depth.get(c["to"], 0), d + 1)
                walk(c["to"], d + 1, req, seen | {c["to"]})

        walk(j["entry"], 0, True, {j["entry"]})
        soft -= hard
        on_path = {j["entry"]} | hard | ({j["via"]} if "via" in j else set())
        needed = j["target"] ** (1 / len(on_path)) if "target" in j else None
        journeys.append({"journey": j, "hard": sorted(hard), "soft": sorted(soft), "depth": max(depth.values(), default=0) + ("via" in j),
                         "needed": needed,
                         "availability_hard": 0.999 ** len(on_path),
                         "availability_all": 0.999 ** (len(on_path) + len(soft))})

    os.makedirs(OUT, exist_ok=True)
    write_services(model, services, teams, adrs)
    write_containers(model, services)
    write_calls(model, adrs)
    write_topics(model)
    write_journeys(journeys)
    write_teams(model, services)
    write_summary(model)
    write_service_cards(model)
    write_open_fallbacks(model)
    write_policies(model)
    write_impact(model, journeys)
    write_stores(model)
    write_contracts(model)

    assumed = [s["name"] for s in model["services"] if s.get("assumed_owner")] + \
              ["%s -> %s" % (c["from"], c["to"]) for c in model["calls"] if c.get("assumed")] + \
              [t["name"] for t in model["topics"] if t.get("assumed")]
    print("model: %d teams, %d services, %d platform components, %d synchronous calls, %d topics and command channels"
          % (len(teams), len(services), len(platform), len(model["calls"]), len(model["topics"])))
    print("ADRs referenced: %d of %d in the handbook; not referenced by the model: %s" % (
        len(referenced), len(adrs), ", ".join("%d" % a for a in sorted(set(adrs) - referenced))))
    for j in journeys:
        if j["needed"]:
            print("journey %s: SLO %.1f%% over %d required service(s) needs %.3f%% from each" % (j["journey"]["name"], 100 * j["journey"]["target"], len(j["hard"]) + 1 + ("via" in j["journey"]), 100 * j["needed"]))
        print("journey %-12s entry %-16s depth %d  required %s  optional %s  availability at 99.9%% each: %.2f%% required only, %.2f%% if every call were required"
              % (j["journey"]["name"], j["journey"]["entry"], j["depth"], j["hard"], j["soft"], 100 * j["availability_hard"], 100 * j["availability_all"]))
    print("assumed (no chapter decided it): %d facts: %s" % (len(assumed), "; ".join(assumed)))
    for e in errors:
        print("ERROR " + e)
    print("errors: %d" % len(errors))
    return 1 if errors else 0


ADRS = {}


def link(adr):
    title, page = ADRS[adr]
    return '[ADR-%03d](../%s "%s")' % (adr, page, title.replace('"', "'"))


def chapter(n):
    f = glob.glob(os.path.join(ROOT, "docs", "*", "ch%02d-*.md" % n))[0]
    return "[chapter %d](../%s)" % (n, os.path.relpath(f, os.path.join(ROOT, "docs")))


def write(name, text):
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
        f.write(text.rstrip() + "\n")


def write_services(model, services, teams, adrs):
    rows = ["| Service | Kind | Team | Purpose | Stores | Decided in |", "|---|---|---|---|---|---|"]
    for s in model["services"]:
        stores = "<br>".join("%s: %s" % (x["type"], x["use"]) for x in s.get("stores", [])) or "–"
        team = s["team"] + (" *" if s.get("assumed_owner") else "")
        rows.append("| `%s` | %s | %s | %s | %s | %s |" % (s["name"], s["kind"], team, s["purpose"], stores, ", ".join(link(a) for a in s.get("adrs", []))))
    write("services.md", "\n".join(rows))
    rows = ["| Component | Team | Purpose | Decided in |", "|---|---|---|---|"]
    for p in model["platform"]:
        rows.append("| `%s` | %s | %s | %s |" % (p["name"], p["team"], p["purpose"], ", ".join(link(a) for a in p["adrs"])))
    write("platform.md", "\n".join(rows))


def write_teams(model, services):
    rows = ["| Team | Type | People | Owns |", "|---|---|---|---|"]
    for t in model["teams"]:
        owned = [s["name"] for s in model["services"] if s["team"] == t["name"]] + \
                [p["name"] for p in model["platform"] if p["team"] == t["name"]]
        rows.append("| %s | %s | %d | %s |" % (t["name"], t["type"], t["people"], ", ".join("`%s`" % o for o in owned) or "nothing (by design)"))
    write("teams.md", "\n".join(rows))


def code_mentions():
    """chapter module -> text of its main sources, for finding where each service's code lives"""
    texts = {}
    for d in sorted(glob.glob(os.path.join(ROOT, "code", "chapters", "ch*"))):
        parts = []
        for f in glob.glob(os.path.join(d, "src", "main", "**", "*.*"), recursive=True):
            if f.endswith((".java", ".yaml", ".yml", ".properties", ".json", ".sql")):
                parts.append(open(f, encoding="utf-8", errors="replace").read())
        texts[os.path.basename(d)] = "\n".join(parts)
    return texts


def write_service_cards(model):
    texts = code_mentions()
    out = []
    for s in model["services"]:
        names = [s["name"]] + s.get("aliases", [])
        pattern = re.compile(r"(?<![A-Za-z0-9-])(%s)(?![A-Za-z0-9-])" % "|".join(re.escape(n) for n in names))
        hits = sorted(((len(pattern.findall(t)), m) for m, t in texts.items() if pattern.search(t)), reverse=True)
        incoming = [c for c in model["calls"] if c["to"] == s["name"]]
        outgoing = [c for c in model["calls"] if c["from"] == s["name"]]
        produces = [t for t in model["topics"] if t["producer"] == s["name"]]
        consumes = [t for t in model["topics"] if s["name"] in t["consumers"] and t["producer"] != s["name"]]
        out.append("### `%s`" % s["name"])
        out.append("")
        owner = s["team"] + (" (assumed)" if s.get("assumed_owner") else "")
        out.append("**%s** · %s · owned by **%s**%s" % (s["kind"], s.get("context", "–") + " context" if s.get("context") else "no bounded context of its own",
                                                    owner, " · also called " + ", ".join("`%s`" % a for a in s["aliases"]) if s.get("aliases") else ""))
        out.append("")
        out.append(s["purpose"])
        out.append("")
        rows = ["| | |", "|---|---|"]
        if s.get("tech"):
            rows.append("| Built with | %s |" % s["tech"])
        rows.append("| Called by | %s |" % (", ".join("`%s` (%s)" % (c["from"].replace("client:", ""), c["protocol"]) for c in incoming) or "nobody synchronously"))
        rows.append("| Calls | %s |" % (", ".join("`%s` (%s%s)" % (c["to"].replace("external:", ""), c["protocol"], "; if it fails: " + c["fallback"] if c.get("fallback") else "") for c in outgoing) or "nothing synchronously"))
        rows.append("| Publishes | %s |" % (", ".join("`%s`" % t["name"] for t in produces) or "–"))
        rows.append("| Consumes | %s |" % (", ".join("`%s`" % t["name"] for t in consumes) or "–"))
        rows.append("| Stores | %s |" % ("<br>".join("%s: %s" % (x["type"], x["use"]) for x in s.get("stores", [])) or "nothing of its own"))
        if s.get("sidecars"):
            rows.append("| Sidecars | %s |" % ", ".join("`%s`" % x for x in s["sidecars"]))
        rows.append("| Decided in | %s |" % ", ".join(link(a) for a in s.get("adrs", [])))
        if hits:
            rows.append("| Code in | %s |" % ", ".join("%s (%d)" % (chapter(int(m[2:4])), n) for n, m in hits[:6]) + (" and %d more" % (len(hits) - 6) if len(hits) > 6 else ""))
        else:
            rows.append("| Code in | no chapter's code names it |")
        out.extend(rows)
        out.append("")
    write("service-cards.md", "\n".join(out))


def write_open_fallbacks(model):
    """calls with no recorded degradation mode, which ADR-002 says every synchronous dependency must declare"""
    journeys_of = {}
    for c in model["calls"]:
        for j in c.get("journey", []):
            journeys_of.setdefault(c["to"], set()).add(j)
    rows = ["| Caller | Dependency | Protocol | On a journey | The question to answer |", "|---|---|---|---|---|"]
    count = {}
    for c in model["calls"]:
        count[c["protocol"]] = count.get(c["protocol"], 0) + 1
        if c.get("fallback") or c["from"].startswith("client:"):
            continue
        on = ", ".join(sorted(set(c.get("journey", [])))) or "–"
        if c["from"] == "edge-gateway":
            q = "none possible: the BFF is the experience; make it as available as the edge"
        elif c["to"].startswith("external:"):
            q = "decided in the ambassador's policy (ADR-017); record it in the model"
        elif c["from"].endswith("-mcp"):
            q = "return a tool error the assistant can explain"
        elif c["from"].endswith("-bff"):
            q = "which screens still work without `%s`?" % c["to"]
        else:
            q = "can `%s` answer without `%s`, from a copy or a default?" % (c["from"], c["to"])
        rows.append("| `%s` | `%s` | %s | %s | %s |" % (c["from"], c["to"].replace("external:", ""), c["protocol"], on, q))
    write("open-fallbacks.md", "\n".join(rows))
    write("protocols.md", "\n".join(["| Protocol | Calls |", "|---|---|"] + ["| %s | %d |" % (k, v) for k, v in sorted(count.items(), key=lambda kv: -kv[1])]))


def namespace(model, name):
    s = next(x for x in model["services"] if x["name"] == name)
    return s.get("namespace", name)


def principal(model, name):
    return "cluster.local/ns/%s/sa/%s" % (namespace(model, name), name)


def write_policies(model):
    """least-privilege rules derived from the model: Istio AuthorizationPolicies (ADR-038, ADR-057) and Kafka ACLs"""
    docs, rows = [], ["| Service | Namespace | May be called by (SPIFFE principal) |", "|---|---|---|"]
    for s in model["services"]:
        if s["kind"] == "function":
            continue
        ns = namespace(model, s["name"])
        callers = sorted({c["from"] for c in model["calls"] if c["to"] == s["name"] and not c["from"].startswith("client:")})
        public = any(c["to"] == s["name"] and c["from"].startswith("client:") for c in model["calls"])
        sources = [principal(model, c) for c in callers]
        if public:
            sources.append("cluster.local/ns/istio-ingress/sa/ingress-gateway")
        docs.append("# %s: %s" % (s["name"], "callers from foodfleet.yaml" if sources else "no synchronous callers in the model: nothing allowed"))
        docs.append("apiVersion: security.istio.io/v1\nkind: AuthorizationPolicy\nmetadata:\n  name: %s\n  namespace: %s\nspec:\n  selector:\n    matchLabels:\n      app: %s\n  action: ALLOW" % (s["name"], ns, s["name"]))
        if sources:
            docs.append("  rules:\n    - from:\n        - source:\n            principals:\n" + "\n".join('              - "%s"' % x for x in sources))
        else:
            docs.append("  rules: []")
        docs.append("---")
        rows.append("| `%s` | `%s` | %s |" % (s["name"], ns, "<br>".join("`%s`" % x for x in sources) or "nobody, in the model"))
    os.makedirs(os.path.join(OUT, "policies"), exist_ok=True)
    with open(os.path.join(OUT, "policies", "authorization-policies.yaml"), "w", encoding="utf-8") as f:
        f.write("# Generated by check.py from foodfleet.yaml. One ALLOW policy per workload; with ALLOW policies present,\n"
                "# Istio denies every request no rule matches. Paths and methods come from each API's OpenAPI document.\n"
                + "\n".join(docs[:-1]) + "\n")
    write("peer-policy.md", "\n".join(rows))
    acl = ["| Principal | Write | Read |", "|---|---|---|"]
    services = sorted({t["producer"] for t in model["topics"]} | {c for t in model["topics"] for c in t["consumers"]},
                      key=[x["name"] for x in model["services"]].index)
    lines = []
    for svc in services:
        w = [t["name"] for t in model["topics"] if t["producer"] == svc]
        r = [t["name"] for t in model["topics"] if svc in t["consumers"]]
        acl.append("| `User:%s` | %s | %s |" % (svc, ", ".join("`%s`" % x for x in w) or "–", ", ".join("`%s`" % x for x in r) or "–"))
        lines += ["%s WRITE %s" % (svc, x) for x in w] + ["%s READ %s" % (svc, x) for x in r]
    write("kafka-acls.md", "\n".join(acl))
    with open(os.path.join(OUT, "policies", "kafka-acls.txt"), "w", encoding="utf-8") as f:
        f.write("# Generated by check.py: principal OPERATION topic. Everything else is denied (allow.everyone.if.no.acl.found=false).\n" + "\n".join(lines) + "\n")


def write_impact(model, journeys):
    """what each journey does when one service is down: fails, degrades (a fallback) or doesn't notice"""
    names = [s["name"] for s in model["services"] if s["kind"] != "function"]
    head = "| Service down | " + " | ".join(j["journey"]["name"] for j in journeys) + " |"
    rows = [head, "|---|" + "---|" * len(journeys)]
    for n in names:
        cells = []
        for j in journeys:
            jj = j["journey"]
            required = {jj["entry"]} | set(j["hard"]) | ({jj["via"]} if "via" in jj else set())
            cells.append("**fails**" if n in required else "degrades" if n in j["soft"] else "·")
        if any(c != "·" for c in cells):
            rows.append("| `%s` | %s |" % (n, " | ".join(cells)))
    write("impact.md", "\n".join(rows))


def write_stores(model):
    rows = ["| Service | Team | Store | What it holds |", "|---|---|---|---|"]
    for s in model["services"]:
        for x in s.get("stores", []):
            rows.append("| `%s` | %s | %s | %s |" % (s["name"], s["team"], {"postgresql": "PostgreSQL", "redis": "Redis"}[x["type"]], x["use"]))
    write("stores.md", "\n".join(rows))


def write_contracts(model):
    """the consumer-driven contracts the model implies (ADR-027, ADR-063): one per consumer and provider"""
    rows = ["| Provider | Consumers with a contract | How |", "|---|---|---|"]
    n_http = n_msg = 0
    for s in model["services"]:
        http = sorted({c["from"] for c in model["calls"] if c["to"] == s["name"] and not c["from"].startswith(("client:", "external:"))})
        msgs = sorted({c for t in model["topics"] if t["producer"] == s["name"] for c in t["consumers"] if c != s["name"]})
        if not http and not msgs:
            continue
        n_http += len(http)
        n_msg += len(msgs)
        parts = []
        if http:
            protos = sorted({c["protocol"] for c in model["calls"] if c["to"] == s["name"] and c["from"] in http})
            parts.append("%s: %s" % ("/".join(protos), ", ".join("`%s`" % x for x in http)))
        if msgs:
            parts.append("messages: %s" % ", ".join("`%s`" % x for x in msgs))
        how = []
        if any(p in ("REST", "GraphQL") for p in [c["protocol"] for c in model["calls"] if c["to"] == s["name"]]):
            how.append("Pact (ADR-063)")
        if any(c["protocol"] == "gRPC" for c in model["calls"] if c["to"] == s["name"]):
            how.append("buf breaking on the .proto (ADR-023)")
        if msgs:
            how.append("AsyncAPI and the registry's FULL_TRANSITIVE check (ADR-027)")
        rows.append("| `%s` | %s | %s |" % (s["name"], "<br>".join(parts), "; ".join(how)))
    rows.append("| **Total** | **%d synchronous, %d message** consumer–provider pairs | |" % (n_http, n_msg))
    write("contracts.md", "\n".join(rows))


def write_summary(model):
    kinds = [("edge", "Edge", "the public entry points"), ("bff", "Backends for frontends", "one per client experience"),
             ("service", "Services", "the business capabilities"), ("platform", "Shared AI platform", "model calls"),
             ("function", "Functions", "event-driven and bursty work")]
    rows = ["| Kind | How many | Which |", "|---|---|---|"]
    for k, title, what in kinds:
        names = [s["name"] for s in model["services"] if s["kind"] == k]
        rows.append("| **%s** (%s) | %d | %s |" % (title, what, len(names), ", ".join("`%s`" % n for n in names)))
    rows.append("| **Platform components** (no business data) | %d | %s |" % (len(model["platform"]), ", ".join("`%s`" % p["name"] for p in model["platform"])))
    rows.append("| **Synchronous calls** | %d | [communication map](communication-map.md) |" % len(model["calls"]))
    rows.append("| **Event topics and saga command channels** | %d | [Kafka topic design](kafka-design.md) |" % len(model["topics"]))
    write("summary.md", "\n".join(rows))


def node_id(name):
    return re.sub(r"[^A-Za-z0-9]", "_", name)


def write_containers(model, services):
    """the synchronous calls between deployables (events are in topics.md: as a graph they are unreadable)"""
    def group(s):
        if s["name"].endswith("-mcp") or s["kind"] == "platform":
            return "ai"
        return s["kind"]

    lines = ["flowchart LR"]
    clients = []
    for c in model["calls"]:
        if c["from"].startswith("client:") and c["from"] not in clients:
            clients.append(c["from"])
    lines.append('    subgraph clients["Clients"]')
    for c in clients:
        lines.append('        %s(["%s"])' % (node_id(c), c.split(":")[1]))
    lines.append("    end")
    for kind, title in (("edge", "Edge"), ("bff", "BFFs"), ("service", "Services"), ("ai", "AI: gateway and MCP servers")):
        members = [s for s in model["services"] if group(s) == kind]
        lines.append('    subgraph %s["%s"]' % ("g_" + kind, title))
        for s in members:
            lines.append('        %s["%s<br/><small>%s</small>"]' % (node_id(s["name"]), s["name"], s["team"]))
        lines.append("    end")
    lines.append('    ext_provider[("payment provider")]')
    arrows = {"REST": "-->", "gRPC": "==>", "GraphQL": "-->", "SSE": "-.->", "WebSocket": "-.->"}
    for c in model["calls"]:
        a = node_id(c["from"]) if not c["from"].startswith("external:") else "ext_provider"
        b = node_id(c["to"]) if not c["to"].startswith("external:") else "ext_provider"
        lines.append("    %s %s|%s| %s" % (a, arrows[c["protocol"]], c["protocol"], b))
    write("containers.mmd", "\n".join(lines))



def write_calls(model, adrs):
    rows = ["| From | To | Protocol | If it fails | Source |", "|---|---|---|---|---|"]
    for c in model["calls"]:
        src = link(c["adr"]) if "adr" in c else chapter(c["ch"])
        if c.get("assumed"):
            src += " (assumed)"
        via = " via `%s`" % c["via"] if c.get("via") else ""
        rows.append("| `%s` | `%s`%s | %s | %s | %s |" % (c["from"], c["to"], via, c["protocol"], c.get("fallback", "–"), src))
    write("calls.md", "\n".join(rows))


def write_topics(model):
    rows = ["| Topic or channel | Kind | Key | Producer | Consumers | Source |", "|---|---|---|---|---|---|"]
    for t in model["topics"]:
        src = link(t["adr"]) if "adr" in t else chapter(t["ch"])
        if t.get("assumed"):
            src += " (assumed)"
        parts = " (%d partitions)" % t["partitions"] if t.get("partitions") else ""
        name = "`%s`" % t["name"] + ("<br><small>also: %s</small>" % ", ".join(t["aliases"]) if t.get("aliases") else "")
        rows.append("| %s | %s | %s%s | `%s` | %s | %s |" % (name, t["kind"], t["key"], parts, t["producer"],
                                                         ", ".join("`%s`" % c for c in t["consumers"]), src))
    write("topics.md", "\n".join(rows))


def write_journeys(journeys):
    rows = ["| Journey | Priority | Required on the path | Optional (with a fallback) | Depth | If each is 99.9%: required only · if all were required | Each must reach, for the SLO |",
            "|---|---|---|---|---|---|---|"]
    for j in journeys:
        jj = j["journey"]
        rows.append("| %s | %s | %s | %s | %d | **%.2f%%** · %.2f%% | %s |" % (
            jj["name"], jj["priority"], ", ".join("`%s`" % x for x in ([jj["via"]] if "via" in jj else []) + [jj["entry"]] + j["hard"]),
            ", ".join("`%s`" % x for x in j["soft"]) or "–", j["depth"], 100 * j["availability_hard"], 100 * j["availability_all"],
            "%.3f%%" % (100 * j["needed"]) if j["needed"] else "– (no SLO)"))
    write("journeys.md", "\n".join(rows))


if __name__ == "__main__":
    sys.exit(main())
