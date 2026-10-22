# Chapter 11 code — Service discovery

Docs: `docs/architecture-patterns/ch11-service-discovery-patterns.md`

| Part | What it shows |
|---|---|
| `simulation/` | Monte Carlo over timer phases: how long traffic keeps reaching a dead instance with Eureka (defaults and tuned), DNS + health checks, and Kubernetes (crash vs hang), and what a retry on another instance changes |
| `kitchen/` | Client-side discovery: an HTTP service client group named after the service id, resolved by Spring Cloud LoadBalancer through the DiscoveryClient; `@Retryable` moves a failed call to the next instance |
| `application-kubernetes.yml` | Platform discovery: the Service DNS name as base URL, no client-side balancing |
| `k8s/kitchen-service.yaml` | ClusterIP and headless Services, readiness probe, `preStop` sleep |

```bash
cd code
mvn -B -pl chapters/ch11-service-discovery -am verify

# the comparison table (plain Java, no Spring needed)
mvn -q -pl chapters/ch11-service-discovery compile exec:java -Dexec.mainClass=dev.foodfleet.ch11.simulation.FailoverReport
# or: java -cp chapters/ch11-service-discovery/target/classes dev.foodfleet.ch11.simulation.FailoverReport
```

Output (verified):

```text
One of 4 instances dies; 100 req/s spread evenly (25 req/s to the dead one)

Mechanism                                    mean s    p50 s    p99 s     failed     slowed
Eureka, defaults                              242.5    242.5    298.1       6062          0
Eureka, tuned                                  35.0     35.0     41.5        875          0
DNS + health checks                            55.0     55.0     81.9       1375          0
Kubernetes Service, crash                       3.0      3.0      3.0         75          0
Kubernetes Service, hang                       27.0     27.0     31.9        675          0
Kubernetes Service, hang + retry elsewhere     27.0     27.0     31.9          0        675
```
