package dev.foodfleet.ch26.experiment;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** A TCP relay in front of a server that counts bytes in each direction and connections opened. */
public final class ByteCountingProxy implements AutoCloseable {

    private final ServerSocket server;
    private final int targetPort;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicLong up = new AtomicLong();
    private final AtomicLong down = new AtomicLong();
    private final AtomicInteger connections = new AtomicInteger();

    public ByteCountingProxy(int targetPort) throws IOException {
        this.targetPort = targetPort;
        this.server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        executor.submit(this::acceptLoop);
    }

    public int port() {
        return server.getLocalPort();
    }

    public long bytesUp() {
        return up.get();
    }

    public long bytesDown() {
        return down.get();
    }

    public int connections() {
        return connections.get();
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket client = server.accept();
                Socket target = new Socket(InetAddress.getLoopbackAddress(), targetPort);
                client.setTcpNoDelay(true);
                target.setTcpNoDelay(true);
                connections.incrementAndGet();
                executor.submit(() -> pump(client, target, up));
                executor.submit(() -> pump(target, client, down));
            } catch (IOException e) {
                return;
            }
        }
    }

    private static void pump(Socket from, Socket to, AtomicLong counter) {
        byte[] buf = new byte[16_384];
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            int n;
            while ((n = in.read(buf)) >= 0) {
                counter.addAndGet(n);
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (IOException ignored) {
            // one side closed
        } finally {
            try {
                to.shutdownOutput();
            } catch (IOException ignored) {
                // already closed
            }
        }
    }

    @Override
    public void close() throws IOException {
        server.close();
        executor.shutdownNow();
    }
}
