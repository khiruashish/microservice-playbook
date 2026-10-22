package dev.foodfleet.menu;

import com.sun.net.httpserver.HttpServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Tests with nothing but the JDK: starts the service on a free port, calls it over HTTP, checks the answers.
 * Run: {@code java -cp out/main:out/test dev.foodfleet.menu.MenuServiceTest} (exit code 0 when all pass).
 */
public final class MenuServiceTest {

    static int failures;

    static void check(String name, boolean ok, String detail) {
        System.out.printf("%s %s%s%n", ok ? "PASS" : "FAIL", name, ok ? "" : "  -> " + detail);
        failures += ok ? 0 : 1;
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = MenuService.start(0);
        String base = "http://localhost:" + server.getAddress().getPort();
        HttpClient http = HttpClient.newHttpClient();
        try {
            HttpResponse<String> menu = http.send(HttpRequest.newBuilder(URI.create(base + "/restaurants/r-1/menu")).build(), HttpResponse.BodyHandlers.ofString());
            check("menu returns 200 and three items", menu.statusCode() == 200 && menu.body().split("\"id\"").length == 4, menu.body());
            HttpResponse<String> veg = http.send(HttpRequest.newBuilder(URI.create(base + "/restaurants/r-1/menu?veg=true")).build(), HttpResponse.BodyHandlers.ofString());
            check("?veg=true leaves out the biryani", !veg.body().contains("biryani") && veg.body().contains("Paneer"), veg.body());
            HttpResponse<String> missing = http.send(HttpRequest.newBuilder(URI.create(base + "/restaurants/r-9/menu")).build(), HttpResponse.BodyHandlers.ofString());
            check("unknown restaurant is a 404 problem document", missing.statusCode() == 404
                    && missing.headers().firstValue("Content-Type").orElse("").equals("application/problem+json") && missing.body().contains("\"status\":404"), missing.body());
            HttpResponse<String> post = http.send(HttpRequest.newBuilder(URI.create(base + "/restaurants/r-1/menu")).POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            check("POST is a 405 problem document", post.statusCode() == 405, post.body());
            boolean rejected;
            try {
                new MenuItem("m-x", "Free lunch", 0, true);
                rejected = false;
            } catch (IllegalArgumentException e) {
                rejected = true;
            }
            check("a menu item can't cost nothing", rejected, "accepted a zero price");
        } finally {
            server.stop(0);
        }
        System.out.println(failures == 0 ? "all tests passed" : failures + " test(s) failed");
        System.exit(failures == 0 ? 0 : 1);
    }
}
