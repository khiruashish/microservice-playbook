import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Lab 14's downstream: menu, pricing and delivery fee in one stand-in, each answering after 50 ms, on virtual
 * threads so it is never the bottleneck. java Downstream.java [port, default 8095] [delay ms, default 50]
 */
public final class Downstream {

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8095;
        long delay = args.length > 1 ? Long.parseLong(args[1]) : 50;
        System.setProperty("sun.net.httpserver.maxIdleConnections", "4000");   // the default, 200, drops keep-alives
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 4096);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/menu/", ex -> answer(ex, delay, "{\"items\":[{\"id\":1,\"qty\":2}]}"));
        server.createContext("/price", ex -> answer(ex, delay, "{\"subtotal\":45000}"));
        server.createContext("/delivery-fee", ex -> answer(ex, delay, "{\"fee\":3000}"));
        server.start();
        System.out.println("downstream on " + port + ", " + delay + " ms per call");
    }

    static void answer(HttpExchange ex, long delay, String body) throws IOException {
        ex.getRequestBody().readAllBytes();
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
