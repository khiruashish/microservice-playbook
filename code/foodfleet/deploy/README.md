# FoodFleet's deployment, generated from the platform model

- `foodfleet-service/`: the shared Helm chart with the platform's ADRs applied (probes and Actuator port, ADR-052;
  image and JVM flags, ADR-053; resources, spread, PDB and config mounts, ADR-054; rollout and checksum, ADR-055;
  HPA behaviour, ADR-056; AOT cache, ADR-060; team labels, ADR-070).
- `values/`: one values file per deployable, written by `generate.py` from `../model/foodfleet.yaml`. Services a
  journey can't work without get three replicas minimum, one per zone; the rest two.
- `conformance.py`: 20 checks, one per rule, each naming its ADR.

```bash
python3 code/foodfleet/deploy/generate.py --helm "$(command -v helm)"      # renders into build/rendered/
python3 code/foodfleet/deploy/conformance.py code/foodfleet/deploy/build/rendered
# with a cluster: kubectl apply --dry-run=server -n <namespace> -f build/rendered/<service>.yaml
```

Image digests in the values files are placeholders; CI writes real ones (ADR-055). Mesh AuthorizationPolicies come
from `../model/generated/policies/`.
