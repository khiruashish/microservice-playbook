import com.sun.net.httpserver.HttpServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;

/**
 * The training run: starts menu-service on a free port, sends it 3,000 requests over HTTP (what a container's first
 * minutes look like), and exits. Run with {@code -XX:AOTCacheOutput}, {@code -XX:ArchiveClassesAtExit} or the
 * native-image agent. A separate main class, so the native image of {@link StartupLab} doesn't contain the client.
 */
public final class Trainer {

    public static void main(String[] args) throws Exception {
        HttpServer server = StartupLab.start(0);
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        int port = server.getAddress().getPort();
        for (int i = 0; i < 3000; i++) {
            String path = i % 100 == 0 ? "/actuator/prometheus" : "/menu?r=" + (i % 500);
            http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(5)).build(),
                    HttpResponse.BodyHandlers.discarding());
        }
        StartupLab.LOG.info("training run: 3000 requests served");
        server.stop(0);
        ((ExecutorService) server.getExecutor()).shutdown();
    }
}
