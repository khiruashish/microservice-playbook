package dev.foodfleet.ch76.functions;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * The smallest function runtime: one function behind POST /, as a FaaS platform's runtime would host it, plus
 * GET /health. Usage: FunctionHost <port> <function class>.
 */
public final class FunctionHost {

    public static void main(String[] args) throws Exception {
        @SuppressWarnings("unchecked")
        Function<String, String> function = (Function<String, String>) Class.forName(args[1]).getDeclaredConstructor().newInstance();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", Integer.parseInt(args[0])), 256);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/health", ex -> {
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.createContext("/", ex -> {
            byte[] out = function.apply(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        if (Boolean.getBoolean("ff.exitEndpoint")) {
            // for training runs: exit normally so -XX:AOTCacheOutput writes its cache
            server.createContext("/exit", ex -> {
                ex.sendResponseHeaders(200, -1);
                ex.close();
                server.stop(0);
                System.exit(0);
            });
        }
        server.start();
    }
}
