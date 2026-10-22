#!/usr/bin/env python3
"""Writes a values file per deployable from the platform model, renders each with the shared chart, and checks the
rendered manifests against the platform's ADRs.

    python3 code/foodfleet/deploy/generate.py [--helm PATH]     (from the repository root; needs helm 3.8+ or 4)

Digests are placeholders (sha256 of the name): in the real pipeline CI writes the built image's digest (ADR-055).
"""
import argparse
import hashlib
import os
import subprocess
import sys

import yaml

HERE = os.path.dirname(os.path.abspath(__file__))
MODEL = os.path.join(HERE, "..", "model")
sys.path.insert(0, MODEL)
import check  # noqa: E402  (the model's checker: journeys and required services)


def required_services(model):
    """services some journey can't work without, from check.py's journey walk"""
    out = set()
    for j in model["journeys"]:
        hard, soft = set(), set()

        def walk(node, required, seen):
            for c in model["calls"]:
                if c["from"] != node or c["to"] in seen or c["to"].startswith(("client:", "external:")):
                    continue
                if node == j["entry"] and j["name"] not in c.get("journey", []):
                    continue
                req = required and not c.get("fallback", "").startswith(("hide", "last known", "place the order without", "positions without"))
                (hard if req else soft).add(c["to"])
                walk(c["to"], req, seen | {c["to"]})

        walk(j["entry"], True, {j["entry"]})
        out |= hard | {j["entry"]} | ({j["via"]} if "via" in j else set())
    return out


def values_for(s, critical):
    v = {"name": s["name"], "team": s["team"],
         "image": {"digest": "sha256:" + hashlib.sha256(s["name"].encode()).hexdigest()},
         "autoscaling": {"minReplicas": 3 if critical else 2, "maxReplicas": 12 if critical else 6}}
    notes = ["minReplicas 3: on a journey's required path, one per zone (ADR-054, ADR-056)" if critical
             else "minReplicas 2: not on any journey's required path"]
    if s.get("_grpc"):
        v["grpc"] = True
        notes.append("gRPC on 9090: called over gRPC (ADR-023)")
    if s["name"] == "tracking-gateway":
        v["resources"] = {"cpu": "1", "memory": "1536Mi"}
        v["shutdown"] = {"preStopSeconds": 5, "gracePeriodSeconds": 75}
        notes.append("1.5 GiB: ~20,000 streams in about 1 GB per instance; grace 75 s: streams drain over 60 s (ADR-026)")
    return v, notes


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--helm", default="helm")
    args = ap.parse_args()
    model = yaml.safe_load(open(os.path.join(MODEL, "foodfleet.yaml"), encoding="utf-8"))
    critical = required_services(model)
    for s in model["services"]:
        s["_grpc"] = any(c["to"] == s["name"] and c["protocol"] == "gRPC" for c in model["calls"])
    vdir, rdir = os.path.join(HERE, "values"), os.path.join(HERE, "build", "rendered")
    os.makedirs(vdir, exist_ok=True)
    os.makedirs(rdir, exist_ok=True)
    rows = []
    for s in model["services"]:
        if s["kind"] == "function":       # Lambda, not Kubernetes (ADR-076)
            continue
        v, notes = values_for(s, s["name"] in critical)
        ns = s.get("namespace", s["name"])
        path = os.path.join(vdir, s["name"] + ".yaml")
        with open(path, "w", encoding="utf-8") as f:
            f.write("# Generated from foodfleet.yaml by generate.py. %s\n" % "; ".join(notes))
            f.write("# namespace: %s\n" % ns)
            yaml.safe_dump(v, f, sort_keys=False)
        out = subprocess.run([args.helm, "template", s["name"], os.path.join(HERE, "foodfleet-service"),
                              "-f", path, "--namespace", ns], capture_output=True, text=True)
        if out.returncode:
            print(out.stderr)
            return 1
        with open(os.path.join(rdir, s["name"] + ".yaml"), "w", encoding="utf-8") as f:
            f.write(out.stdout)
        rows.append((s["name"], ns, v["autoscaling"]["minReplicas"]))
    print("rendered %d services: %d with minReplicas 3, %d with 2" % (
        len(rows), sum(1 for r in rows if r[2] == 3), sum(1 for r in rows if r[2] == 2)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
