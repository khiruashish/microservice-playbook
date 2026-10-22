"""Lab 20: before the game day, ask FoodFleet's real alert rules (code/foodfleet/slo/foodfleet-slo.rules.yml) which
of the planned faults will page, and when. Each fault is turned into checkout traffic at 20 orders a second, and
promtool evaluates the rules minute by minute. Standard library, PyYAML and promtool.
  python3 paging.py <promtool>"""
import pathlib, re, subprocess, sys, tempfile
import yaml

HERE = pathlib.Path(__file__).resolve().parent
RULES = HERE.parents[2] / "foodfleet" / "slo" / "foodfleet-slo.rules.yml"
RATE = 20 * 60            # checkouts per minute
BASELINE = 0.0002         # 0.02% of checkouts fail on a normal day
START = 3 * 24 * 60 + 10   # three days of normal traffic first, so even the 3-day window has history

# name, error ratio during the fault, minutes
FAULTS = [
    ("payment-service down", 1.0, 10),
    ("payment latency: 5% of checkouts time out", 0.05, 30),
    ("one bad pod of 10: 1% fail", 0.01, 30),
    ("a slow leak: 0.3% fail", 0.003, 360),
]


def series(error_ratio, minutes, horizon):
    ok, bad, ok_vals, bad_vals = 0, 0, [], []
    for m in range(horizon + 1):
        ratio = error_ratio if START <= m < START + minutes else BASELINE
        ok_vals.append(ok)
        bad_vals.append(bad)
        bad += RATE * ratio
        ok += RATE * (1 - ratio)
    common = 'job="order-service", uri="/orders", method="POST"'
    return [
        {"series": f'http_server_requests_seconds_count{{{common}, outcome="SUCCESS"}}', "values": " ".join(f"{v:.0f}" for v in ok_vals)},
        {"series": f'http_server_requests_seconds_count{{{common}, outcome="SERVER_ERROR"}}', "values": " ".join(f"{v:.1f}" for v in bad_vals)},
    ]


def first_alerts(name, ratio, minutes):
    horizon = START + minutes + 120
    test = {"interval": "1m", "input_series": series(ratio, minutes, horizon), "alert_rule_test": [
        {"eval_time": f"{m}m", "alertname": "CheckoutAvailabilityErrorBudgetBurn", "exp_alerts": []}
        for m in range(START - 30, horizon + 1)]}
    with tempfile.TemporaryDirectory() as d:
        f = pathlib.Path(d, "t.yml")
        f.write_text(yaml.safe_dump({"rule_files": [str(RULES)], "evaluation_interval": "1m", "tests": [test]}))
        r = subprocess.run([sys.argv[1], "test", "rules", str(f)], capture_output=True, text=True)
        out = r.stdout + r.stderr
    firing = {}                      # severity -> (first minute, last minute) the alert was firing
    for block in out.split("alertname: ")[1:]:
        t = re.search(r"time: (?:(\d+)d)?(?:(\d+)h)?(?:(\d+)m)?", block)
        m = int(t.group(1) or 0) * 1440 + int(t.group(2) or 0) * 60 + int(t.group(3) or 0)
        for sev in set(re.findall(r'severity="(\w+)"', block.split("got:")[-1])):
            first, last = firing.get(sev, (m, m))
            firing[sev] = (min(first, m), max(last, m))
    budget = ratio * minutes / (0.001 * 30 * 24 * 60)
    return firing, budget, START + minutes + 120


print(f"checkout at 20 orders/s, {BASELINE:.2%} errors normally; FoodFleet's checkout SLO 99.9% over 30 days")
print("minutes from the fault's start; each fault is followed by two hours of normal traffic")
print(f"{'fault':44} {'for':>6} {'budget spent':>13}   {'page':>22}   {'ticket':>22}")
for name, ratio, minutes in FAULTS:
    firing, budget, horizon = first_alerts(name, ratio, minutes)
    def show(sev):
        if sev not in firing:
            return "never"
        first, last = firing[sev]
        return f"+{first - START} min, " + (f"until +{last - START + 1}" if last < horizon else "still at the end")
    print(f"{name:44} {minutes:4} m {budget:12.1%}   {show('page'):>22}   {show('ticket'):>22}")
