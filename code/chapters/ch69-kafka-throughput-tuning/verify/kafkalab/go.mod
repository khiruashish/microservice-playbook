module foodfleet.dev/ch69/kafkalab

go 1.24.0

require (
	github.com/twmb/franz-go v1.20.6
	github.com/twmb/franz-go/pkg/kfake v0.0.0-20260218054735-2437c0f42b2e
	github.com/twmb/franz-go/pkg/kmsg v1.12.0
)

require (
	github.com/klauspost/compress v1.18.4 // indirect
	github.com/pierrec/lz4/v4 v4.1.25 // indirect
	golang.org/x/crypto v0.48.0 // indirect
)

// golang.org/x modules from their GitHub mirrors, for machines that reach GitHub but not golang.org
replace golang.org/x/crypto => github.com/golang/crypto v0.48.0
