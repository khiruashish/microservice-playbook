package dev.foodfleet.ch46.cell;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes each tenant (a restaurant chain, a city) to one cell: a complete, independent copy of FoodFleet's stack with
 * its own databases. A fault, a bad deploy or a poison request in one cell reaches only that cell's tenants.
 * Placement is rendezvous hashing (each tenant goes to the cell with the highest hash of tenant + cell), so adding
 * a cell moves only the tenants the new cell wins; pinned tenants and drained cells override it.
 */
public final class CellRouter {

    private final List<String> cells;
    private final Map<String, String> pinned = new ConcurrentHashMap<>();
    private final Set<String> drained = ConcurrentHashMap.newKeySet();

    public CellRouter(List<String> cells) {
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("at least one cell");
        }
        this.cells = List.copyOf(cells);
    }

    public String cellOf(String tenant) {
        String p = pinned.get(tenant);
        if (p != null && !drained.contains(p)) {
            return p;
        }
        String best = null;
        long bestScore = Long.MIN_VALUE;
        for (String cell : cells) {
            if (drained.contains(cell)) {
                continue;
            }
            long score = hash(tenant + "|" + cell);
            if (best == null || score > bestScore) {
                best = cell;
                bestScore = score;
            }
        }
        return Objects.requireNonNull(best, "every cell is drained");
    }

    /** Keeps a tenant in a cell (a large chain moved deliberately, a tenant under investigation). */
    public void pin(String tenant, String cell) {
        if (!cells.contains(cell)) {
            throw new IllegalArgumentException("unknown cell " + cell);
        }
        pinned.put(tenant, cell);
    }

    /** Sends a cell's tenants elsewhere: the cell is broken or being rebuilt. Their data must move with them. */
    public void drain(String cell) {
        drained.add(cell);
    }

    public void undrain(String cell) {
        drained.remove(cell);
    }

    static long hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (d[i] & 0xff);
            }
            return v;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
