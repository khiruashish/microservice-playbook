package dev.foodfleet.ch38.spiffe;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

/** TLS 1.3 contexts and sockets for SPIFFE mutual TLS. */
public final class SpiffeTls {

    private SpiffeTls() {
    }

    public static SSLContext context(KeyManager keys, TrustManager trust) {
        try {
            SSLContext ctx = SSLContext.getInstance("TLSv1.3");
            ctx.init(new KeyManager[] {keys}, new TrustManager[] {trust}, null);
            return ctx;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A server socket that requires a client certificate: no certificate, no connection. */
    public static SSLServerSocket mutualTlsServer(SSLContext ctx, int port) throws IOException {
        SSLServerSocket server = (SSLServerSocket) ctx.getServerSocketFactory().createServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress("127.0.0.1", port));
        SSLParameters p = server.getSSLParameters();
        p.setProtocols(new String[] {"TLSv1.3"});
        p.setNeedClientAuth(true);
        server.setSSLParameters(p);
        return server;
    }

    public static SSLSocket connect(SSLContext ctx, int port) throws IOException {
        SSLSocket s = (SSLSocket) ctx.getSocketFactory().createSocket("127.0.0.1", port);
        SSLParameters p = s.getSSLParameters();
        p.setProtocols(new String[] {"TLSv1.3"});
        s.setSSLParameters(p);
        s.setTcpNoDelay(true);
        s.startHandshake();
        return s;
    }
}
