package dev.foodfleet.orders;

import dev.foodfleet.orders.adapter.menu.InMemoryMenu;
import dev.foodfleet.orders.adapter.web.HttpAdapter;
import dev.foodfleet.orders.app.OrderService;
import dev.foodfleet.orders.domain.Order;
import dev.foodfleet.orders.domain.OrderLine;
import dev.foodfleet.orders.domain.Pricing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** order-service: 1,000 orders of 20 lines each over a 50,000-item menu. Usage: Application <port> */
public final class Application {

    public static void main(String[] args) throws Exception {
        InMemoryMenu menu = new InMemoryMenu(50_000);
        OrderService service = new OrderService(new Pricing(menu));
        for (int i = 0; i < 1_000; i++) {
            List<OrderLine> lines = new ArrayList<>();
            for (int l = 0; l < 20; l++) {
                lines.add(new OrderLine("item-" + ((i * 37 + l * 1_009) % 50_000), 1 + l % 3));
            }
            service.add(new Order("o-" + i, "r-" + (i % 50), lines, Instant.parse("2026-03-16T12:00:00Z").plusSeconds(i), "PLACED"));
        }
        new HttpAdapter(service).start(Integer.parseInt(args[0]));
        System.out.println("ready");
    }
}
