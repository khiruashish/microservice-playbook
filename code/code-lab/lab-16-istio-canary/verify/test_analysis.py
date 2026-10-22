"""Checks the AnalysisTemplate's PromQL with promtool's unit tests: the queries are read from rollouts/analysis.yaml,
so what's tested is what Argo Rollouts will run. Four canaries, measured at minute 5 over synthetic Istio metrics.
  python3 test_analysis.py <promtool>"""
import pathlib, subprocess, sys, tempfile
import yaml

HERE = pathlib.Path(__file__).resolve().parent
template = yaml.safe_load((HERE.parent / "rollouts" / "analysis.yaml").read_text())
queries = {m["name"]: m["provider"]["prometheus"]["query"] for m in template["spec"]["metrics"]}
conditions = {m["name"]: (m["successCondition"], m["failureCondition"]) for m in template["spec"]["metrics"]}

CANARY = 'destination_service="menu-service-canary.menu-service.svc.cluster.local"'
STABLE = 'destination_service="menu-service-stable.menu-service.svc.cluster.local"'


def requests(service, code, per_second):
    return {"series": f'istio_requests_total{{reporter="destination", {service}, response_code="{code}"}}',
            "values": f"0+{per_second * 15:g}x40"}


def latency(service, per_second, le_of_all):
    """Every request in the bucket ending at le_of_all (ms); cumulative counts for the other buckets."""
    out = []
    for le in ("50", "100", "200", "500", "+Inf"):
        inside = le == "+Inf" or float(le) >= le_of_all
        out.append({"series": f'istio_request_duration_milliseconds_bucket{{reporter="destination", {service}, le="{le}"}}',
                    "values": f"0+{per_second * 15 if inside else 0:g}x40"})
    return out


# name: (input series, expected error-rate, expected p99 ratio)
scenarios = {
    "healthy, no 5xx at all": (
        [requests(CANARY, 200, 10), requests(STABLE, 200, 90)] + latency(CANARY, 10, 100) + latency(STABLE, 90, 100), 0, 1),
    "5% errors": (
        [requests(CANARY, 200, 9.5), requests(CANARY, 503, 0.5), requests(STABLE, 200, 90)]
        + latency(CANARY, 10, 100) + latency(STABLE, 90, 100), 0.05, 1),
    "too little traffic (60 requests a minute)": (
        [requests(CANARY, 200, 1), requests(STABLE, 200, 99)] + latency(CANARY, 1, 100) + latency(STABLE, 99, 100), -1, -1),
    "slow: p99 in the 100-200 ms bucket": (
        [requests(CANARY, 200, 10), requests(STABLE, 200, 90)] + latency(CANARY, 10, 200) + latency(STABLE, 90, 100),
        0, (100 + 0.99 * 100) / (50 + 0.99 * 50)),     # interpolated within the buckets: 199 / 99.5
}

tests = []
for name, (series, error_rate, p99) in scenarios.items():
    tests.append({"interval": "15s", "input_series": series, "promql_expr_test": [
        {"expr": queries["error-rate"], "eval_time": "5m", "exp_samples": [{"labels": "{}", "value": error_rate}]},
        {"expr": queries["p99-vs-stable"], "eval_time": "5m", "exp_samples": [{"labels": "{}", "value": p99}]},
    ]})

with tempfile.TemporaryDirectory() as d:
    rules = pathlib.Path(d, "none.yml")
    rules.write_text("groups: []\n")
    test = pathlib.Path(d, "analysis-test.yml")
    test.write_text(yaml.safe_dump({"rule_files": [str(rules)], "tests": tests}, sort_keys=False))
    result = subprocess.run([sys.argv[1], "test", "rules", str(test)], capture_output=True, text=True)
print(result.stdout.strip() or result.stderr.strip())


def verdict(value, name):
    ok, bad = conditions[name]
    env = {"result": [value]}
    expr = lambda c: eval(c.replace("&&", "and"), {}, env)
    return "success" if expr(ok) else "failure" if expr(bad) else "inconclusive"


print(f"{'canary':44} {'error-rate':>10} {'verdict':>13} {'p99 ratio':>10} {'verdict':>13}")
for name, (_, error_rate, p99) in scenarios.items():
    print(f"{name:44} {error_rate:10.3f} {verdict(error_rate, 'error-rate'):>13} {p99:10.3f} {verdict(p99, 'p99-vs-stable'):>13}")
sys.exit(result.returncode)
