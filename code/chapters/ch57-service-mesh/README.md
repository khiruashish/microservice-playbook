# Chapter 57 — Service Mesh

Code and measurements for [chapter 57](../../../docs/deployment/ch57-service-mesh.md).

| Path | What |
|---|---|
| `src/main/java/.../mesh/` | `SpiffeId` (workload identities) and `Sidecar` (Envoy bootstrap configurations: outbound with per-request balancing over a headless Service, retries on another pod, outlier detection and mTLS; inbound requiring mTLS from listed identities) |
| `src/main/resources/envoy/` | Two generated examples: the caller's outbound sidecar and menu-service's inbound one |
| `src/test/java/` | 4 tests |
| `verify/` | `gen-certs.sh` (a lab mesh CA and SPIFFE certificates), `build-libs.sh` (Envoy 1.39.4 and MeshLab on chapter 54's runtime; Envoy and Kubernetes source clones), `run.sh` (validates every configuration with `envoy --mode validate` first), `check_apis.sh` (9 checks), `tools/` (`MeshLab`, `Measure`) |

```bash
K3S_DISABLE=traefik ../../ch52-health-checks-actuator/verify/k3s-lab.sh   # a single-node cluster with metrics-server
JAVA_HOME=/path/to/jdk-25 verify/run.sh                                   # about 20 minutes
```

## Findings (k3s v1.37.1+k3s1, one 2-vCPU node, Envoy 1.39.4 as native sidecars, JDK 25.0.4, two runs)

**balance**: keep-alive client, 100 requests/s, scaled 1 → 3 at 10 s (new pods ready at 14 s).

- Through kube-proxy: 1,000 of 1,000 requests per window on the first pod, for all 60 s.
- Through the sidecar: 851–878 / 61–75 / 61–74 in 10–20 s, then 333 / 333 / 334 in every window.
- p50: 1.19–1.28 ms through kube-proxy, 2.57–2.60 ms through two sidecars with mTLS. No failures.

**identity**: only TLS from the mesh CA with the identity `sa/gateway` (or the caller's sidecar) got a 200. Plaintext, no certificate, `sa/reporting`, and `sa/gateway` from another CA were refused. The app's port refused connections when the app listened on 127.0.0.1 and answered 200 when it listened on 0.0.0.0. A request on a new connection: 1.1–1.3 ms plaintext, 6.9–7.9 ms with a full mTLS handshake (p50).

**resilience**: 2 good pods, 1 answering 503; 50 requests/s for 60 s.

| Caller | Failed of 3,000 | Retries | Ejections |
|---|---|---|---|
| kube-proxy, no mesh | 988–1,027 | – | – |
| Sidecar, nothing on | 1,000 | 0 | – |
| 2 retries on another pod | 0 | 1,496–1,497 | – |
| Outlier detection, 50% max | 26 | 0 | 2 |
| Outlier detection, default 10% max | 1,000 | 0 | 0 |
| Both | 0 | 35 | 2 |

**overhead**: one pod, keep-alive, 500 requests/s for 50 s after a 10 s warm-up.

| Path | p50 | p99 |
|---|---|---|
| No proxy | 0.50–0.58 ms | 3.8–5.6 ms |
| 1 proxy | 0.98–1.03 ms | 4.9–9.4 ms |
| 2 proxies, plaintext | 1.46–1.49 ms | 11.4–13.3 ms |
| 2 proxies, mTLS | 1.52–1.53 ms | 21.0–21.1 ms |

At 500 requests/s with mTLS: menu-service's app 97–100m CPU and 65–68 MiB; its Envoy 217m and 32–41 MiB; the caller's Envoy 208–215m and 37–44 MiB.

**apis**: 9 of 9 found in Envoy 1.39.4 and Kubernetes 1.37.1.

## Notes

- There's no control plane: `Sidecar` writes the configuration istiod would push, and the experiments call the sidecar on localhost instead of redirecting traffic with iptables.
- The first version of the handshake probe measured ~50 ms for mTLS: Nagle's algorithm held the HTTP request until a delayed ACK. `MeshLab` now sets `TCP_NODELAY`.
- Why retries alone cost ~1,500 retries and not ~1,000: each retry advanced Envoy's round robin, so the bad pod received every other first attempt.
- The certificates are lab-only, made by `gen-certs.sh` with one-day lifetimes.
