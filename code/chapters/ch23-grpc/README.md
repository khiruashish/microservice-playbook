# Chapter 23 — gRPC & Protocol Buffers

tracking-service's `OrderTracking` API (`src/main/proto/foodfleet/tracking/v1/tracking.proto`): a unary
`GetStatus` and a server-streaming `WatchOrder`. Three parts:

| Package | What it is |
|---|---|
| `wire/` | `ProtoWriter` / `ProtoReader`: the protobuf wire format by hand, so you can see it. `PayloadComparison`: protobuf vs JSON sizes, raw and gzipped, for one status and a 30-minute ride of 360 positions |
| `deadline/` | `DeadlineSimulation`: tracking → eta → maps with fixed per-hop timeouts vs a propagated deadline, while maps is slow |
| `grpc/` | Spring gRPC: `OrderTrackingService` (`@GrpcService`), `GrpcServerConfig` (a `@GlobalServerInterceptor` and a `GrpcExceptionHandler`), `TrackingClient` (generated blocking stub, deadline on every call) |

`verify/verify_wire.py` rebuilds the same schema with Google's protobuf runtime and checks the hand-written
encoder byte for byte, the canonical JSON mapping, and unknown-field handling.

## Run

```bash
cd code
mvn -B -pl chapters/ch23-grpc -am verify          # generates the stubs, runs the tests
java -cp chapters/ch23-grpc/target/classes dev.foodfleet.ch23.wire.PayloadComparison
java -cp chapters/ch23-grpc/target/classes dev.foodfleet.ch23.deadline.DeadlineSimulation
mvn -pl chapters/ch23-grpc spring-boot:run         # gRPC server on 9090
grpcurl -plaintext localhost:9090 list             # reflection is on (grpc-services)
grpcurl -plaintext -d '{"order_id":"o-1"}' localhost:9090 foodfleet.tracking.v1.OrderTracking/GetStatus
pip install protobuf && python3 chapters/ch23-grpc/verify/verify_wire.py <hex printed by PayloadComparison>
```

The last `grpcurl` returns `NOT_FOUND`: the store starts empty (in FoodFleet it is fed from Kafka events).

## Verified output

Measured on a JDK 21 runtime (the module targets JDK 25; these classes use no newer APIs); Python protobuf 7.34.1 for the cross-check.

```text
bytes on the wire                                 protobuf      JSON   ratio
one OrderStatus                                        104       255   2.45x
one OrderStatus, gzip                                  124       217   1.75x
30-min ride: 360 positions, raw                      16920     43114   2.55x
30-min ride, each message gzipped (gRPC-style)       23840     44563   1.87x
30-min ride, whole stream gzipped at once             5558      3539   0.64x
```

```text
official protobuf runtime: 104 bytes
Java ProtoWriter:          104 bytes
byte-for-byte identical:   True
official parser reads the Java bytes back equal: True
v1 reader on v2 bytes: fields it knows intact: True | unknown field kept, re-serialised: True
```

```text
tracking -> eta -> maps; app deadline 500 ms; 10,000 requests per row
maps behaviour         mode                    succeeded maps busy ms/req      wasted ms/req    p99 held ms
healthy                FIXED_HOP_TIMEOUTS         100.0%               40                  0            130
healthy                PROPAGATED_DEADLINE        100.0%               40                  0            130
60% slow (~1.5 s)      FIXED_HOP_TIMEOUTS          39.3%              928                639           1841
60% slow (~1.5 s)      PROPAGATED_DEADLINE         39.3%              291                  2            504
```

## Tests

| Test | Checks |
|---|---|
| `ProtoWireTest` | bytes equal the official runtime's; field numbers on the wire; proto3 defaults omitted; an old reader skips a new field; the gzip findings |
| `DeadlineSimulationTest` | no difference when healthy; propagated deadlines cut wasted work without changing success |
| `OrderTrackingServiceTest` | `@SpringBootTest` + `@AutoConfigureTestGrpcTransport`: status, `NOT_FOUND` / `INVALID_ARGUMENT`, streaming until delivered, and a passed deadline cancelling the server's subscription |
