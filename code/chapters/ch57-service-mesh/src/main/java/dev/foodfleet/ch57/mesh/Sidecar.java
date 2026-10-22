package dev.foodfleet.ch57.mesh;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Envoy bootstrap configurations for a sidecar, the part of a service mesh that a control plane (istiod, Linkerd's
 * destination service) would otherwise generate and push: an <em>outbound</em> listener the application calls on
 * localhost, balancing each request across the upstream's pods, with retries, outlier detection and mTLS; and an
 * <em>inbound</em> listener in front of the application that terminates mTLS and admits only listed identities.
 */
public final class Sidecar {

    /** Certificates for mTLS; {@code peers}: the identities allowed on the other end. */
    public record Tls(String certFile, String keyFile, String caFile, List<SpiffeId> peers) {
    }

    /** Retries on 5xx, connection failures and resets, each on a different pod. */
    public record Retry(int attempts, Duration perTryTimeout) {
    }

    /** Eject a pod after {@code consecutive5xx} errors in a row, for {@code baseEjection} times the ejection count. */
    public record Outlier(int consecutive5xx, Duration interval, Duration baseEjection, int maxEjectionPercent) {
    }

    /** An upstream service, resolved through DNS to every pod (a headless Service). Null parts are left out. */
    public record Upstream(String name, String dnsName, int port, Retry retry, Outlier outlier, Tls tls) {
    }

    private Sidecar() {
    }

    public static String outbound(int listenPort, Upstream u) {
        String retry = u.retry() == null ? "" : """
                                  retry_policy:
                                    retry_on: 5xx,connect-failure,reset
                                    num_retries: %d
                                    per_try_timeout: %s
                                    retry_host_predicate:
                                      - name: envoy.retry_host_predicates.previous_hosts
                                        typed_config: {"@type": type.googleapis.com/envoy.extensions.retry.host.previous_hosts.v3.PreviousHostsPredicate}
                                    host_selection_retry_max_attempts: 5
                """.formatted(u.retry().attempts(), seconds(u.retry().perTryTimeout())).indent(10);
        String outlier = u.outlier() == null ? "" : """
                      outlier_detection:
                        consecutive_5xx: %d
                        interval: %s
                        base_ejection_time: %s
                        max_ejection_percent: %d
                """.formatted(u.outlier().consecutive5xx(), seconds(u.outlier().interval()), seconds(u.outlier().baseEjection()),
                u.outlier().maxEjectionPercent());
        String tls = u.tls() == null ? "" : """
                      transport_socket:
                        name: envoy.transport_sockets.tls
                        typed_config:
                          "@type": type.googleapis.com/envoy.extensions.transport_sockets.tls.v3.UpstreamTlsContext
                %s
                """.formatted(context(u.tls(), "          "));
        return admin() + """
                static_resources:
                  listeners:
                    - name: outbound
                      address: {socket_address: {address: 0.0.0.0, port_value: %d}}
                      filter_chains:
                        - filters:
                            - name: envoy.filters.network.http_connection_manager
                              typed_config:
                                "@type": type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager
                                stat_prefix: outbound
                                route_config:
                                  virtual_hosts:
                                    - name: %s
                                      domains: ["*"]
                                      routes:
                                        - match: {prefix: "/"}
                                          route:
                                            cluster: %s
                                            timeout: 10s
                %s                http_filters:
                                  - name: envoy.filters.http.router
                                    typed_config: {"@type": type.googleapis.com/envoy.extensions.filters.http.router.v3.Router}
                  clusters:
                    - name: %s
                      type: STRICT_DNS
                      dns_refresh_rate: 2s
                      respect_dns_ttl: false
                      lb_policy: ROUND_ROBIN
                      connect_timeout: 1s
                      load_assignment:
                        cluster_name: %s
                        endpoints: [{lb_endpoints: [{endpoint: {address: {socket_address: {address: %s, port_value: %d}}}}]}]
                %s%s""".formatted(listenPort, u.name(), u.name(), retry, u.name(), u.name(), u.dnsName(), u.port(), outlier, tls);
    }

    public static String inbound(int listenPort, int appPort, Tls tls) {
        String downstream = tls == null ? "" : """
                          transport_socket:
                            name: envoy.transport_sockets.tls
                            typed_config:
                              "@type": type.googleapis.com/envoy.extensions.transport_sockets.tls.v3.DownstreamTlsContext
                              require_client_certificate: true
                %s
                """.formatted(context(tls, "              "));
        return admin() + """
                static_resources:
                  listeners:
                    - name: inbound
                      address: {socket_address: {address: 0.0.0.0, port_value: %d}}
                      filter_chains:
                        - filters:
                            - name: envoy.filters.network.http_connection_manager
                              typed_config:
                                "@type": type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager
                                stat_prefix: inbound
                                route_config:
                                  virtual_hosts:
                                    - name: app
                                      domains: ["*"]
                                      routes: [{match: {prefix: "/"}, route: {cluster: app, timeout: 10s}}]
                                http_filters:
                                  - name: envoy.filters.http.router
                                    typed_config: {"@type": type.googleapis.com/envoy.extensions.filters.http.router.v3.Router}
                %s  clusters:
                    - name: app
                      type: STATIC
                      connect_timeout: 1s
                      load_assignment:
                        cluster_name: app
                        endpoints: [{lb_endpoints: [{endpoint: {address: {socket_address: {address: 127.0.0.1, port_value: %d}}}}]}]
                """.formatted(listenPort, downstream, appPort);
    }

    /** The certificate this sidecar presents, the CA it trusts, and the peer identities it accepts. */
    static String context(Tls tls, String indent) {
        String peers = tls.peers().stream().map(p -> "    - {san_type: URI, matcher: {exact: \"" + p + "\"}}").collect(Collectors.joining("\n"));
        String text = """
                common_tls_context:
                  tls_certificates: [{certificate_chain: {filename: %s}, private_key: {filename: %s}}]
                  validation_context:
                    trusted_ca: {filename: %s}
                    match_typed_subject_alt_names:
                %s""".formatted(tls.certFile(), tls.keyFile(), tls.caFile(), peers);
        return text.lines().map(l -> indent + l).collect(Collectors.joining("\n"));
    }

    static String admin() {
        return "admin: {address: {socket_address: {address: 0.0.0.0, port_value: 15000}}}\n";
    }

    static String seconds(Duration d) {
        return d.toMillis() % 1000 == 0 ? d.toSeconds() + "s" : String.format("%.3fs", d.toMillis() / 1000.0);
    }
}
