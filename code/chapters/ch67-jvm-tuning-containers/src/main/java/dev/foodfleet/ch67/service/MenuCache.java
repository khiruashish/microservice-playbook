package dev.foodfleet.ch67.service;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * menu-service's heap, in miniature: a cache of restaurant menus (the live set: millions of small objects that stay
 * for minutes) and requests that build responses from it (short-lived garbage) and refresh entries now and then (new
 * objects that live long enough to be promoted). The kind of heap that decides how a collector behaves.
 */
public final class MenuCache {

    public record Item(String name, long pricePaise, boolean veg) {
    }

    public record Menu(long restaurantId, List<Item> items) {
    }

    static final int ITEMS = 20;

    private final AtomicReferenceArray<Menu> menus;

    /** A cache of about {@code liveMb} megabytes (one menu is about 1.7 KB with ordinary object headers). */
    public MenuCache(int liveMb) {
        int n = Math.max(1, liveMb * 1024 / 17 * 10);
        menus = new AtomicReferenceArray<>(n);
        SplittableRandom r = new SplittableRandom(42);
        for (int i = 0; i < n; i++) {
            menus.set(i, menu(i, r));
        }
    }

    public int size() {
        return menus.length();
    }

    static Menu menu(long id, SplittableRandom r) {
        List<Item> items = new ArrayList<>(ITEMS);
        for (int j = 0; j < ITEMS; j++) {
            items.add(new Item("dish-" + id + "-" + j + "-" + r.nextInt(1000), 9_900 + r.nextInt(40_000), r.nextBoolean()));
        }
        return new Menu(id, items);
    }

    /**
     * One request: render five menus as JSON (garbage), and one time in {@code refreshEvery} replace a menu (the old one
     * becomes garbage in the old generation). Returns the response length, so nothing is optimised away.
     */
    public int handle(SplittableRandom r, int refreshEvery) {
        int n = menus.length();
        if (r.nextInt(refreshEvery) == 0) {
            int i = r.nextInt(n);
            menus.set(i, menu(i, r));
        }
        StringBuilder json = new StringBuilder(8192).append('[');
        for (int k = 0; k < 5; k++) {
            Menu m = menus.get(r.nextInt(n));
            json.append("{\"restaurant\":").append(m.restaurantId()).append(",\"items\":[");
            for (Item it : m.items()) {
                json.append("{\"name\":\"").append(it.name()).append("\",\"price\":").append(it.pricePaise() / 100.0).append(",\"veg\":").append(it.veg()).append("},");
            }
            json.append("]},");
        }
        return json.append(']').toString().length();
    }
}
