package dev.foodfleet.ch39.web;

import com.zaxxer.hikari.HikariCredentialsProvider;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import com.zaxxer.hikari.util.Credentials;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.LeaseListener;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseEvent;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;
import org.springframework.vault.core.lease.event.SecretLeaseRotatedEvent;

/**
 * Dynamic database credentials that keep working past the role's max_ttl. Spring Cloud Vault's
 * {@code spring.cloud.vault.database} support requests a <em>renewable</em> secret: renewed until max_ttl, then gone
 * (read from the 5.0.2 sources; measured in verify/). This class requests a <em>rotating</em> secret instead, so Spring
 * Vault fetches new credentials before the old ones expire, and hands them to HikariCP through a
 * {@link HikariCredentialsProvider} (HikariCP 7), which is asked for credentials on every new connection. On each
 * rotation the pool soft-evicts its connections, so they move to the new user well before the old one is revoked.
 */
public final class VaultDatabaseCredentials implements HikariCredentialsProvider, LeaseListener {

    private static final Log log = LogFactory.getLog(VaultDatabaseCredentials.class);

    private final RequestedSecret secret;
    private volatile Credentials current = Credentials.of(null, null);
    private volatile HikariDataSource pool;

    public VaultDatabaseCredentials(SecretLeaseContainer leases, String credsPath) {
        leases.addLeaseListener(this);                       // before the request: the first event may come at once
        this.secret = leases.requestRotatingSecret(credsPath);
    }

    /** The pool to refresh after a rotation. */
    void attach(HikariDataSource dataSource) {
        this.pool = dataSource;
    }

    @Override
    public Credentials getCredentials() {
        return current;
    }

    @Override
    public void onLeaseEvent(SecretLeaseEvent event) {
        if (event.getSource() != secret) {
            return;
        }
        if (event instanceof SecretLeaseCreatedEvent created) {            // first credentials, and every rotation
            Map<String, Object> s = created.getSecrets();
            current = Credentials.of((String) s.get("username"), (String) s.get("password"));
            log.info("database credentials " + (event instanceof SecretLeaseRotatedEvent ? "rotated" : "obtained")
                    + " for " + s.get("username"));                     // the user name, never the password
            HikariDataSource ds = pool;
            HikariPoolMXBean mx = ds == null ? null : ds.getHikariPoolMXBean();
            if (mx != null) {
                mx.softEvictConnections();       // idle connections close now, busy ones when returned
            }
        } else if (event instanceof SecretLeaseExpiredEvent) {
            log.error("database credentials expired before they could be rotated: " + secret.getPath());
        }
    }
}
