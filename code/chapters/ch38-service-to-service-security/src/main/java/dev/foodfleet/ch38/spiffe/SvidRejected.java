package dev.foodfleet.ch38.spiffe;

import java.security.cert.CertificateException;

/** A peer certificate that is not an acceptable X.509-SVID, with the reason (logged, never sent to the peer). */
public final class SvidRejected extends CertificateException {

    private static final long serialVersionUID = 1L;

    public SvidRejected(String reason) {
        super(reason);
    }

    public SvidRejected(String reason, Throwable cause) {
        super(reason, cause);
    }
}
