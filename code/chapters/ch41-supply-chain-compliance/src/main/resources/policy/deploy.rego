# FoodFleet's deployment rules, checked in CI (conftest/opa) before anything reaches a cluster, and the same rules
# again at admission. Input: one Kubernetes Deployment. Output: deny, a set of messages; empty means compliant.
package foodfleet.deploy

import rego.v1

registry := "registry.foodfleet.in/"

# Indian regions of FoodFleet's clouds: payment data must stay in India (RBI, storage of payment system data);
# personal data does too, by FoodFleet's choice under the DPDP Act (transfers can be restricted by order).
india_regions := {"ap-south-1", "ap-south-2", "asia-south1", "asia-south2"}

# DPDP Rules 2025 (large e-commerce platforms): erase personal data three years after the last interaction
max_personal_retention_days := 1095

containers contains c if some c in input.spec.template.spec.containers

containers contains c if some c in input.spec.template.spec.initContainers

labels := object.get(input.spec.template.metadata, "labels", {})

annotations := object.get(input.spec.template.metadata, "annotations", {})

data_class := object.get(labels, "foodfleet.in/data-class", "")

deny contains msg if {
	some c in containers
	not contains(c.image, "@sha256:")
	msg := sprintf("%s: image %q is not pinned by digest", [c.name, c.image])
}

deny contains msg if {
	some c in containers
	not startswith(c.image, registry)
	msg := sprintf("%s: image %q is not from %s", [c.name, c.image, registry])
}

deny contains msg if {
	not data_class in {"public", "internal", "personal", "payment"}
	msg := "missing or unknown label foodfleet.in/data-class"
}

deny contains msg if {
	data_class in {"personal", "payment"}
	region := object.get(annotations, "foodfleet.in/region", "")
	not region in india_regions
	msg := sprintf("%s data outside India: region %q", [data_class, region])
}

deny contains msg if {
	data_class == "payment"
	input.metadata.namespace != "cde"
	msg := "payment data outside the cardholder data environment namespace (cde)"
}

deny contains msg if {
	data_class == "personal"
	days := to_number(object.get(annotations, "foodfleet.in/retention-days", "0"))
	not days > 0
	msg := "personal data without a retention period (foodfleet.in/retention-days)"
}

deny contains msg if {
	data_class == "personal"
	days := to_number(object.get(annotations, "foodfleet.in/retention-days", "0"))
	days > max_personal_retention_days
	msg := sprintf("retention of %d days exceeds %d", [days, max_personal_retention_days])
}

deny contains msg if {
	some c in containers
	not c.securityContext.runAsNonRoot
	msg := sprintf("%s: runAsNonRoot is not set", [c.name])
}
