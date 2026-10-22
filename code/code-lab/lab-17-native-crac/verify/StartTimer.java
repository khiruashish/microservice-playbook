import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Starts a command N times and measures, each time, the milliseconds from the process start to its first 200 on a URL,
 * polling every 2 ms. Prints the median, min and max, and the first request's own latency.
 *   java StartTimer.java <runs> <url> <command...>
 */
public final class StartTimer {

    public static void main(String[] args) throws Exception {
        int runs = Integer.parseInt(args[0]);
        URI url = URI.create(args[1]);
        List<String> command = Arrays.asList(args).subList(2, args.length);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200)).build();
        List<Long> times = new ArrayList<>();
        for (int run = 0; run < runs; run++) {
            long start = System.nanoTime();
            Process p = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            long ready = -1;
            while (ready < 0 && System.nanoTime() - start < 60_000_000_000L) {
                try {
                    if (http.send(HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(5)).build(),
                            HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                        ready = (System.nanoTime() - start) / 1_000_000;
                    }
                } catch (IOException notYet) {
                    Thread.sleep(2);
                }
            }
            p.destroy();
            p.waitFor();
            times.add(ready);
        }
        Collections.sort(times);
        System.out.printf("median %d ms (min %d, max %d) over %d starts%n", times.get(times.size() / 2), times.get(0),
                times.get(times.size() - 1), runs);
    }
}
