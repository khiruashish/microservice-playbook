#!/usr/bin/env python3
"""Checks rendered manifests against the platform ADRs, one check per rule, like chapter 72's conformance checks.

    python3 code/foodfleet/deploy/conformance.py DIR...     (directories of rendered YAML)
"""
import glob
import os
import re
import sys

import yaml


def checks():
    def dep(docs):
        return next(d for d in docs if d["kind"] == "Deployment")

    def app(docs):
        return dep(docs)["spec"]["template"]["spec"]["containers"][0]

    def kinds(docs):
        return {d["kind"] for d in docs}

    return [
        ("ADR-004/055", "image by digest", lambda d: "@sha256:" in app(d)["image"]),
        ("ADR-052", "liveness on /actuator/health/liveness, not /actuator/health",
         lambda d: app(d)["livenessProbe"]["httpGet"]["path"] == "/actuator/health/liveness"),
        ("ADR-052", "readiness on /actuator/health/readiness", lambda d: app(d)["readinessProbe"]["httpGet"]["path"] == "/actuator/health/readiness"),
        ("ADR-052", "startup probe", lambda d: "startupProbe" in app(d)),
        ("ADR-052", "preStop drain and a grace period longer than it",
         lambda d: app(d)["lifecycle"]["preStop"]["sleep"]["seconds"] < dep(d)["spec"]["template"]["spec"]["terminationGracePeriodSeconds"]),
        ("ADR-052", "Actuator port admitted only from monitoring", lambda d: "NetworkPolicy" in kinds(d)),
        ("ADR-053", "non-root, read-only root filesystem, no capabilities",
         lambda d: dep(d)["spec"]["template"]["spec"]["securityContext"]["runAsNonRoot"]
         and app(d)["securityContext"]["readOnlyRootFilesystem"] and app(d)["securityContext"]["capabilities"]["drop"] == ["ALL"]),
        ("ADR-053", "MaxRAMPercentage and ExitOnOutOfMemoryError",
         lambda d: "-XX:MaxRAMPercentage=" in env(app(d)) and "-XX:+ExitOnOutOfMemoryError" in env(app(d))),
        ("ADR-054", "no CPU limit; memory limit equals request",
         lambda d: "cpu" not in app(d)["resources"].get("limits", {})
         and app(d)["resources"]["limits"]["memory"] == app(d)["resources"]["requests"]["memory"]),
        ("ADR-054", "ActiveProcessorCount = CPU request rounded up",
         lambda d: int(re.search(r"ActiveProcessorCount=(\d+)", env(app(d))).group(1)) == cpus(app(d)["resources"]["requests"]["cpu"])),
        ("ADR-054", "PDB maxUnavailable 1", lambda d: any(x["kind"] == "PodDisruptionBudget" and x["spec"]["maxUnavailable"] == 1 for x in d)),
        ("ADR-054", "zone topology spread", lambda d: any(c["topologyKey"] == "topology.kubernetes.io/zone"
                                                        for c in dep(d)["spec"]["template"]["spec"]["topologySpreadConstraints"])),
        ("ADR-054", "no subPath config mounts", lambda d: all("subPath" not in m for m in app(d).get("volumeMounts", []))),
        ("ADR-055", "no replicas under an HPA", lambda d: "replicas" not in dep(d)["spec"]),
        ("ADR-055", "maxUnavailable 0 on rollout", lambda d: dep(d)["spec"]["strategy"]["rollingUpdate"]["maxUnavailable"] == 0),
        ("ADR-055", "config checksum on the pod template", lambda d: "checksum/config" in dep(d)["spec"]["template"]["metadata"]["annotations"]),
        ("ADR-056", "HPA on CPU at 60% (or KEDA on lag with CPU at 60%), 300 s scale-down window",
         lambda d: any(x["kind"] == "HorizontalPodAutoscaler" and x["spec"]["metrics"][0]["resource"]["target"]["averageUtilization"] == 60
                       and x["spec"]["behavior"]["scaleDown"]["stabilizationWindowSeconds"] == 300 for x in d)
                   or any(x["kind"] == "ScaledObject" and not any(y["kind"] == "HorizontalPodAutoscaler" for y in d)
                          and {t["type"] for t in x["spec"]["triggers"]} == {"kafka", "cpu"}
                          and any(t["type"] == "cpu" and t["metadata"]["value"] == "60" for t in x["spec"]["triggers"])
                          and x["spec"]["advanced"]["horizontalPodAutoscalerConfig"]["behavior"]["scaleDown"]["stabilizationWindowSeconds"] == 300
                          for x in d)),
        ("ADR-059", "a service account per service", lambda d: dep(d)["spec"]["template"]["spec"]["serviceAccountName"] == dep(d)["metadata"]["name"]),
        ("ADR-060", "AOT cache", lambda d: "-XX:AOTCache=" in env(app(d))),
        ("ADR-070", "team label for cost allocation", lambda d: "foodfleet.dev/team" in dep(d)["metadata"]["labels"]),
    ]


def env(c):
    return next(e["value"] for e in c["env"] if e["name"] == "JAVA_TOOL_OPTIONS")


def cpus(v):
    v = str(v)
    return -(-int(v[:-1]) // 1000) if v.endswith("m") else -(-int(float(v) * 1000) // 1000)


def main(dirs):
    files = sorted(f for d in dirs for f in glob.glob(os.path.join(d, "*.yaml")))
    rules = checks()
    failed = 0
    for f in files:
        docs = [d for d in yaml.safe_load_all(open(f, encoding="utf-8")) if d]
        bad = []
        for adr, what, test in rules:
            try:
                ok = test(docs)
            except (KeyError, StopIteration, TypeError, AttributeError):
                ok = False
            if not ok:
                bad.append("%s %s" % (adr, what))
        failed += bool(bad)
        print("%-40s %2d of %d%s" % (os.path.basename(f)[:-5], len(rules) - len(bad), len(rules), ("  FAIL: " + "; ".join(bad)) if bad else ""))
    print("%d of %d manifests conform" % (len(files) - failed, len(files)))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
