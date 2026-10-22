"""Validates the chapter's contract documents with the official JSON Schemas, and sample responses
(as Jackson 3 writes OrderView) against the OpenAPI document's Order schema.

  pip install jsonschema pyyaml
  # schemas: OAI/OpenAPI-Specification (branch v3.1-dev) src/schemas/validation/schema.yaml
  #          asyncapi/spec-json-schemas schemas/3.0.0-without-$id.json
  python3 validate_contracts.py <oas-3.1-schema.yaml> <asyncapi-3.0.0.json> <samples.jsonl>
"""
import json
import pathlib
import sys

import yaml
from jsonschema import Draft7Validator, Draft202012Validator
from jsonschema.validators import validator_for

HERE = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/static/contracts"


def report(label, errors):
    print(f"{label}: " + ("valid" if not errors else f"{len(errors)} errors"))
    for e in errors[:10]:
        print("   ", list(e.path), e.message[:200])


oas_schema = yaml.safe_load(open(sys.argv[1]))
order_api = yaml.safe_load(open(HERE / "order-api.yaml"))
report("order-api.yaml against the OpenAPI 3.1 schema",
       list(validator_for(oas_schema)(oas_schema).iter_errors(order_api)))

asyncapi_schema = json.load(open(sys.argv[2]))
events = yaml.safe_load(open(HERE / "order-events.asyncapi.yaml"))
report("order-events.asyncapi.yaml against the AsyncAPI 3.0.0 schema",
       list(Draft7Validator(asyncapi_schema).iter_errors(events)))

order_schema = order_api["components"]["schemas"]["Order"]
for line in open(sys.argv[3]):
    sample = json.loads(line)
    report(f"response {sample['id']} against components.schemas.Order",
           list(Draft202012Validator(order_schema).iter_errors(sample)))
