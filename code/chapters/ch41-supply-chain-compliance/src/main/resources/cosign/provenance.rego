# cosign verify-attestation --type slsaprovenance1 --policy provenance.rego: the provenance must come from
# FoodFleet's release workflow, run for a version tag of the service's own repository.
package signature

import rego.v1

default allow := false

allow if {
	builder := input.predicate.runDetails.builder.id
	startswith(builder, "https://github.com/foodfleet/")
	contains(builder, "/.github/workflows/release.yml@refs/tags/v")
	input.predicate.buildDefinition.externalParameters.workflow.repository == "https://github.com/foodfleet/order-service"
}
