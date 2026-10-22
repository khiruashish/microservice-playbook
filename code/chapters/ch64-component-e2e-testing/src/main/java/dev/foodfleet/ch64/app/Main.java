package dev.foodfleet.ch64.app;

/**
 * One image, three services: {@code java -cp ... dev.foodfleet.ch64.app.Main menu|order|delivery}, configured by
 * environment variables, listening on 8080.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String env;
        switch (args[0]) {
            case "menu" -> MenuApp.start(8080, Integer.parseInt(System.getenv().getOrDefault("DAILY_STOCK", "5")));
            case "delivery" -> DeliveryApp.start(8080, Double.parseDouble(System.getenv().getOrDefault("ASSIGN_MEDIAN_MS", "300")),
                    Double.parseDouble(System.getenv().getOrDefault("ASSIGN_SIGMA", "1.0")), System.nanoTime());
            case "order" -> {
                env = System.getenv("DB_URL");
                for (int i = 0; ; i++) { // the database may still be starting
                    try {
                        OrderApp.start(8080, System.getenv("MENU_URL"), System.getenv("DELIVERY_URL"), OrderApp.jdbc(env));
                        break;
                    } catch (IllegalStateException e) {
                        if (i == 120) {
                            throw e;
                        }
                        Thread.sleep(500);
                    }
                }
            }
            default -> throw new IllegalArgumentException(args[0]);
        }
        System.out.println(args[0] + " ready");
    }
}
