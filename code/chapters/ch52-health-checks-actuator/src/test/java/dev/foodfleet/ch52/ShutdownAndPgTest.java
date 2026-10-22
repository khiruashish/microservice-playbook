package dev.foodfleet.ch52;

import dev.foodfleet.ch52.availability.Availability;
import dev.foodfleet.ch52.pg.PgPing;
import dev.foodfleet.ch52.shutdown.GracefulShutdown;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShutdownAndPgTest {

    @Test
    void shutdownRefusesReadinessFirstThenDrainsThenWaitsForInFlight() throws Exception {
        Availability a = new Availability();
        a.readiness(Availability.Readiness.ACCEPTING_TRAFFIC);
        GracefulShutdown g = new GracefulShutdown(a, Duration.ofMillis(200), Duration.ofSeconds(5));
        List<String> events = new ArrayList<>();
        Thread request = new Thread(() -> g.track(() -> sleep(500)));
        request.start();
        Thread.sleep(50);
        long t = System.nanoTime();
        int left = g.shutdown(new GracefulShutdown.Server() {
            @Override
            public void stopAccepting() {
                events.add("stop accepting after " + (System.nanoTime() - t) / 100_000_000 + "00 ms, readiness " + a.readiness());
            }

            @Override
            public void close() {
                events.add("close, in flight " + g.inFlight());
            }
        });
        assertThat(left).isEqualTo(0);
        assertThat(events.get(0)).isEqualTo("stop accepting after 200 ms, readiness REFUSING_TRAFFIC");
        assertThat(events.get(1)).isEqualTo("close, in flight 0");
    }

    @Test
    void shutdownGivesUpAfterTheTimeout() throws Exception {
        GracefulShutdown g = new GracefulShutdown(new Availability(), Duration.ZERO, Duration.ofMillis(100));
        Thread request = new Thread(() -> g.track(() -> sleep(2_000)));
        request.start();
        Thread.sleep(50);
        int left = g.shutdown(new GracefulShutdown.Server() {
            @Override
            public void stopAccepting() {
            }

            @Override
            public void close() {
            }
        });
        assertThat(left).isEqualTo(1);
        request.interrupt();
    }

    @Test
    void pgPingSpeaksTheStartupAndSimpleQueryProtocol() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Thread fake = new Thread(() -> fakePostgres(server, false));
            fake.start();
            new PgPing("127.0.0.1", server.getLocalPort(), "postgres", "postgres", Duration.ofSeconds(2)).ping();
            fake.join();
        }
    }

    @Test
    void pgPingFailsWhenAPasswordIsRequired() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Thread fake = new Thread(() -> fakePostgres(server, true));
            fake.start();
            boolean failed = false;
            try {
                new PgPing("127.0.0.1", server.getLocalPort(), "postgres", "postgres", Duration.ofSeconds(2)).ping();
            } catch (IOException e) {
                failed = e.getMessage().contains("password");
            }
            assertThat(failed).isTrue();
            fake.join();
        }
    }

    @Test
    void pgPingFailsFastWhenNothingListens() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        boolean failed = false;
        try {
            new PgPing("127.0.0.1", port, "postgres", "postgres", Duration.ofMillis(500)).ping();
        } catch (IOException e) {
            failed = true;
        }
        assertThat(failed).isTrue();
    }

    static void fakePostgres(ServerSocket server, boolean wantPassword) {
        try (Socket s = server.accept()) {
            DataInputStream in = new DataInputStream(s.getInputStream());
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            int len = in.readInt();
            in.readFully(new byte[len - 4]);
            out.writeByte('R');
            out.writeInt(8);
            out.writeInt(wantPassword ? 3 : 0);                // 3: cleartext password requested; 0: OK
            if (wantPassword) {
                out.flush();
                return;
            }
            out.writeByte('Z');
            out.writeInt(5);
            out.writeByte('I');
            out.flush();
            int type = in.readUnsignedByte();
            len = in.readInt();
            in.readFully(new byte[len - 4]);
            if (type == 'Q') {
                out.writeByte('Z');
                out.writeInt(5);
                out.writeByte('I');
                out.flush();
            }
            in.readUnsignedByte();                              // Terminate
        } catch (IOException e) {
            // the client closed
        }
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
