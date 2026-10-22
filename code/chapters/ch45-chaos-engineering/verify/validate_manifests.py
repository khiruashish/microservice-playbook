#!/usr/bin/env python3
"""Validates the chapter's Chaos Mesh and LitmusChaos manifests against the CRD schemas of the released versions.

Kubernetes prunes fields a CRD's schema doesn't know unless the client asks for strict field validation. kubectl
asks by default; a client library or controller that doesn't gets the field dropped, at most with a warning. So each
manifest is checked twice: against the schema alone (unknown fields ignored) and strictly (unknown fields are errors). A mistyped field passes the first and fails the second; the script shows
that with a deliberately broken copy of the latency experiment.

Usage: validate_manifests.py <chaos-mesh crd.yaml> <litmus chaos_crds.yaml> <manifest>...
Needs PyYAML and jsonschema.
"""
import copy
import sys

import jsonschema
import yaml


def load_crds(paths):
    crds = {}
    for path in paths:
        with open(path) as f:
            for doc in yaml.safe_load_all(f):
                if not doc or doc.get("kind") != "CustomResourceDefinition":
                    continue
                group = doc["spec"]["group"]
                kind = doc["spec"]["names"]["kind"]
                for version in doc["spec"]["versions"]:
                    crds[(f"{group}/{version['name']}", kind)] = version["schema"]["openAPIV3Schema"]
    return crds


def strict(schema):
    """Unknown fields become errors wherever the schema lists its fields and doesn't allow others."""
    s = copy.deepcopy(schema)

    def walk(node):
        if not isinstance(node, dict):
            return
        props = node.get("properties")
        if props is not None and "additionalProperties" not in node \
                and not node.get("x-kubernetes-preserve-unknown-fields"):
            node["additionalProperties"] = False
        for value in (props or {}).values():
            walk(value)
        if isinstance(node.get("items"), dict):
            walk(node["items"])
        if isinstance(node.get("additionalProperties"), dict):
            walk(node["additionalProperties"])

    walk(s)
    # metadata is validated by the API server itself, not by the CRD
    s.setdefault("properties", {})["metadata"] = {"type": "object"}
    return s


def check(crds, doc):
    key = (doc.get("apiVersion"), doc.get("kind"))
    if key not in crds:
        return [f"no CRD for {key}"], []
    schema = crds[key]
    lenient = [e.message for e in jsonschema.Draft7Validator(schema).iter_errors(doc)]
    strict_errors = [f"{'/'.join(map(str, e.absolute_path))}: {e.message}"
                     for e in jsonschema.Draft7Validator(strict(schema)).iter_errors(doc)]
    return lenient, strict_errors


def main():
    crds = load_crds(sys.argv[1:3])
    print(f"{len(crds)} CRD versions loaded")
    failed = 0
    for path in sys.argv[3:]:
        with open(path) as f:
            for doc in yaml.safe_load_all(f):
                lenient, strict_errors = check(crds, doc)
                ok = not lenient and not strict_errors
                failed += 0 if ok else 1
                name = f"{doc['kind']}/{doc['metadata']['name']}"
                print(f"{'OK     ' if ok else 'INVALID'}  {name:<44} {path.split('/')[-1]}")
                for e in lenient + strict_errors:
                    print(f"         {e}")

    # the typo nobody sees: 'durations' instead of 'duration'
    with open(next(p for p in sys.argv[3:] if p.endswith("restaurant-latency.yaml"))) as f:
        broken = yaml.safe_load(f)
    broken["spec"]["durations"] = broken["spec"].pop("duration")
    lenient, strict_errors = check(crds, broken)
    print()
    print("a copy of restaurant-latency with 'durations: 30s' (a typo):")
    print(f"  schema only (an API client that doesn't ask for strict validation): {'accepted' if not lenient else 'rejected'}"
          f"; the field is pruned and, with no duration, the delay runs until someone deletes it")
    print(f"  strict (kubectl's default, fieldValidation=Strict): {'rejected: ' + strict_errors[0] if strict_errors else 'accepted'}")
    if lenient or not strict_errors:
        failed += 1

    print()
    print("all manifests valid" if failed == 0 else f"{failed} problems")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
