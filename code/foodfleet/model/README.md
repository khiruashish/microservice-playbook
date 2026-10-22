# FoodFleet's platform model

`foodfleet.yaml` describes FoodFleet as the handbook's 77 ADRs decided it: teams, services, platform components,
synchronous calls, topics and saga command channels, and user journeys. Every fact names its source (`adr:` or `ch:`);
facts no chapter decided are marked `assumed: true` (or `assumed_owner: true`).

```bash
python3 code/foodfleet/model/check.py     # from the repository root; needs PyYAML
```

`check.py`:

- validates the model: known keys only (an unquoted comma in a YAML flow mapping silently splits a value), teams
  exist, names and aliases are unique, calls and topics connect known services, every cited ADR exists in the
  chapters, no cycles in the synchronous call graph;
- derives each journey's required and optional services, its depth, and the availability those imply;
- writes the Markdown and Mermaid fragments in `generated/`, which the pages under `docs/food-delivery-platform/`
  include. Rerun it after changing the model; the fragments are committed so the docs build without Python.

It exits with status 1 if there is any error.
