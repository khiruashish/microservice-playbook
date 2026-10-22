package foodfleet.deploy_test

import rego.v1

import data.foodfleet.deploy

image := "registry.foodfleet.in/orders/order-service@sha256:c312b8af4989a587fa0c36ba8e7679374744996228a88d095fc8b230bde38dea"

deployment(ns, labels, annotations, img) := {
	"metadata": {"name": "x", "namespace": ns},
	"spec": {"template": {
		"metadata": {"labels": labels, "annotations": annotations},
		"spec": {"containers": [{"name": "app", "image": img, "securityContext": {"runAsNonRoot": true}}]},
	}},
}

test_compliant_personal_service if {
	count(deploy.deny) == 0 with input as deployment("orders",
		{"foodfleet.in/data-class": "personal"},
		{"foodfleet.in/region": "ap-south-1", "foodfleet.in/retention-days": "1095"}, image)
}

test_tag_instead_of_digest if {
	deploy.deny["app: image \"registry.foodfleet.in/orders/order-service:1.4.0\" is not pinned by digest"] with input as deployment("orders",
		{"foodfleet.in/data-class": "internal"}, {}, "registry.foodfleet.in/orders/order-service:1.4.0")
}

test_public_registry if {
	some m in deploy.deny with input as deployment("orders", {"foodfleet.in/data-class": "internal"}, {},
		"docker.io/library/redis@sha256:0000000000000000000000000000000000000000000000000000000000000000")
	contains(m, "is not from registry.foodfleet.in/")
}

test_payment_data_outside_india if {
	deploy.deny["payment data outside India: region \"eu-west-1\""] with input as deployment("cde",
		{"foodfleet.in/data-class": "payment"}, {"foodfleet.in/region": "eu-west-1"}, image)
}

test_payment_outside_cde if {
	deploy.deny["payment data outside the cardholder data environment namespace (cde)"] with input as deployment("orders",
		{"foodfleet.in/data-class": "payment"}, {"foodfleet.in/region": "ap-south-1"}, image)
}

test_retention_missing_and_too_long if {
	deploy.deny["personal data without a retention period (foodfleet.in/retention-days)"] with input as deployment("orders",
		{"foodfleet.in/data-class": "personal"}, {"foodfleet.in/region": "ap-south-1"}, image)
	deploy.deny["retention of 3650 days exceeds 1095"] with input as deployment("orders",
		{"foodfleet.in/data-class": "personal"}, {"foodfleet.in/region": "ap-south-1", "foodfleet.in/retention-days": "3650"}, image)
}

test_unclassified if {
	deploy.deny["missing or unknown label foodfleet.in/data-class"] with input as deployment("orders", {}, {}, image)
}
