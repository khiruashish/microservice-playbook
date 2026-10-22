"""Reads what lab 12's collector wrote (OTLP JSON lines) and reports: traces per order, which hops each kept,
whether the logs carry trace ids, and the RED metrics the collector derived from spans."""
import collections, json, pathlib, sys

out = pathlib.Path(sys.argv[1])

def records(name, kind):
    for line in (out / name).read_text().splitlines():
        for res in json.loads(line)[f"resource{kind}"]:
            attrs = {a["key"]: a["value"].get("stringValue") for a in res["resource"]["attributes"]}
            for scope in res[f"scope{kind}"]:
                for item in scope[{"Spans": "spans", "Logs": "logRecords", "Metrics": "metrics"}[kind]]:
                    yield attrs, item

def attr(span, key):
    for a in span.get("attributes", []):
        if a["key"] == key:
            v = a["value"]
            return v.get("stringValue", v.get("intValue"))

spans = [(r["service.name"], s) for r, s in records("traces.json", "Spans")]
leaked = [r for r, _ in records("traces.json", "Spans") if "process.command_line" in r or "process.command_args" in r]
traces = collections.defaultdict(list)
for service, s in spans:
    traces[s["traceId"]].append((service, s))

def shape(trace):
    return " > ".join(sorted({f"{svc}:{s['name']}" for svc, s in trace}, key=lambda x: ("order" not in x, "menu" not in x, x)))

order_traces = collections.Counter()
kitchen_alone = 0
for trace in traces.values():
    services = {svc for svc, _ in trace}
    roots = [s for svc, s in trace if not s.get("parentSpanId")]
    server = [s for svc, s in trace if svc == "order-service" and s["kind"] == 2]
    if server:
        query = attr(server[0], "url.query") or ""
        kind = "missing" if "missing" in query else "thread" if "thread" in query else "executor"
        order_traces[(kind, tuple(sorted(services)))] += 1
    elif "order-service" in services:
        order_traces[("orphan", tuple(sorted(services)))] += 1   # started on a thread that had no context
    elif services == {"kitchen-service"}:
        kitchen_alone += 1

print(f"spans: {len(spans)}, traces: {len(traces)}; resources still carrying the JVM command line: {len(leaked)}")
for (kind, services), n in sorted(order_traces.items()):
    label = "traces starting at a client span (no request)" if kind == "orphan" else f"{kind} orders"
    print(f"  {label}: {n} traces spanning {', '.join(services)}")

# One complete trace, as a tree
complete = max((t for t in traces.values() if {svc for svc, _ in t} == {"order-service", "menu-service", "kitchen-service"}),
               key=lambda t: min(int(s["startTimeUnixNano"]) for _, s in t))   # the last one: JIT warmed up
by_id = {s["spanId"]: (svc, s) for svc, s in complete}
children = collections.defaultdict(list)
for svc, s in complete:
    children[s.get("parentSpanId", "")].append((svc, s))
def tree(parent, depth):
    for svc, s in sorted(children[parent], key=lambda x: int(x[1]["startTimeUnixNano"])):
        ms = (int(s["endTimeUnixNano"]) - int(s["startTimeUnixNano"])) / 1e6
        kind = {1: "internal", 2: "server", 3: "client"}.get(s["kind"], s["kind"])
        print(f"    {'  ' * depth}{svc:16} {kind:8} {s['name']:<22} {ms:6.1f} ms")
        tree(s["spanId"], depth + 1)
print("  the last executor order, as a tree:")
tree("", 0)

logs = [(r["service.name"], l) for r, l in records("logs.json", "Logs")]
with_trace = [l for _, l in logs if l.get("traceId")]
print(f"logs: {len(logs)}, with a trace id: {len(with_trace)}")
errors = [(svc, l) for svc, l in logs if l.get("severityText") in ("SEVERE", "WARNING")]
for svc, l in errors[:2]:
    print(f"  {svc}: {l['severityText']} '{l['body']['stringValue']}' trace {l.get('traceId', '-')[:8]}…")

red = collections.Counter()
for r, m in records("span-metrics.json", "Metrics"):
    if m["name"] == "traces.span.metrics.calls":
        for dp in m["sum"]["dataPoints"]:
            a = {x["key"]: x["value"].get("stringValue", x["value"].get("intValue")) for x in dp["attributes"]}
            if a.get("span.kind") == "SPAN_KIND_SERVER":
                red[(r["service.name"], a.get("span.name"), str(a.get("http.response.status_code")))] = max(
                    red[(r["service.name"], a.get("span.name"), str(a.get("http.response.status_code")))], int(dp["asInt"]))
print("RED from spans (server calls by status):")
for (svc, name, status), n in sorted(red.items()):
    print(f"  {svc:16} {name:<16} {status}  {n}")
