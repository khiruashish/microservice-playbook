package dev.foodfleet.ch60.menu;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * menu-service's work for one request: a restaurant's menu, priced with today's discount, grouped by category, sorted
 * and rendered as JSON. Ordinary business code (records, streams, BigDecimal, string formatting), which is exactly the
 * kind of code a JIT compiles well after a warm-up and an ahead-of-time compiler compiles once.
 */
public final class MenuCatalog {

    public record Item(String id, String name, String category, BigDecimal price, boolean vegetarian, int popularity) {
    }

    private static final String[] CATEGORIES = {"starters", "mains", "breads", "rice", "desserts", "drinks"};
    private static final String[] WORDS = {"paneer", "tikka", "masala", "dal", "makhani", "butter", "garlic", "naan", "biryani", "kulfi",
            "lassi", "chaat", "dosa", "idli", "vada", "korma", "kebab", "pulao", "raita", "halwa"};

    private MenuCatalog() {
    }

    /** A restaurant's menu: deterministic for a restaurant id, {@code size} items. */
    public static List<Item> menu(int restaurant, int size) {
        List<Item> items = new ArrayList<>(size);
        long seed = restaurant * 2654435761L;
        for (int i = 0; i < size; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int a = (int) ((seed >>> 33) % WORDS.length);
            int b = (int) ((seed >>> 41) % WORDS.length);
            String name = capitalize(WORDS[a]) + " " + capitalize(WORDS[b]);
            BigDecimal price = BigDecimal.valueOf(60 + (seed >>> 50) % 440).add(BigDecimal.valueOf((seed >>> 20) % 100, 2));
            items.add(new Item("r" + restaurant + "-i" + i, name, CATEGORIES[(int) ((seed >>> 13) % CATEGORIES.length)], price, (seed & 1) == 0,
                    (int) ((seed >>> 7) % 1000)));
        }
        return items;
    }

    /** The response body: items priced with the discount, by category, most popular first. */
    public static String render(List<Item> items, int discountPercent) {
        Map<String, List<Item>> byCategory = new TreeMap<>();
        for (Item i : items) {
            byCategory.computeIfAbsent(i.category(), k -> new ArrayList<>()).add(i);
        }
        StringBuilder json = new StringBuilder(items.size() * 120).append("{\"categories\":[");
        boolean firstCategory = true;
        for (Map.Entry<String, List<Item>> e : byCategory.entrySet()) {
            e.getValue().sort(Comparator.comparingInt(Item::popularity).reversed().thenComparing(Item::name));
            json.append(firstCategory ? "" : ",").append("{\"name\":\"").append(e.getKey()).append("\",\"items\":[");
            firstCategory = false;
            boolean first = true;
            for (Item i : e.getValue()) {
                BigDecimal price = discounted(i.price(), discountPercent);
                json.append(first ? "" : ",").append("{\"id\":\"").append(i.id()).append("\",\"name\":\"").append(escape(i.name()))
                        .append("\",\"price\":").append(price.toPlainString()).append(",\"label\":\"")
                        .append(String.format(Locale.ROOT, "Rs %,.2f", price)).append("\",\"veg\":").append(i.vegetarian()).append('}');
                first = false;
            }
            json.append("]}");
        }
        return json.append("]}").toString();
    }

    public static BigDecimal discounted(BigDecimal price, int discountPercent) {
        return price.multiply(BigDecimal.valueOf(100 - discountPercent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_EVEN);
    }

    static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
