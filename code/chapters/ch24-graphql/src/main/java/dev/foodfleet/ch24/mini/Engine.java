package dev.foodfleet.ch24.mini;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small GraphQL executor, enough to show the N+1 problem and how batching fixes it.
 *
 * <p>Execution goes level by level, as graphql-java does with DataLoader: all parents at one level
 * are resolved before their children. A field with a <b>per-item resolver</b> calls it once per
 * parent (N+1). A field with a <b>batch resolver</b>, in {@link Mode#BATCHED}, is called once with
 * every parent at that level, the way a DataLoader collects keys and dispatches them together.
 * Fields with no resolver read the record component of the same name (Spring for GraphQL's default
 * property data fetcher does the same with getters).
 */
public final class Engine {

    public enum Mode { PER_ITEM, BATCHED }

    @FunctionalInterface
    public interface Resolver {
        Object resolve(Object parent, Map<String, Object> arguments);
    }

    @FunctionalInterface
    public interface BatchResolver {
        /** One value per parent, in the parents' order. */
        List<Object> resolve(List<Object> parents, Map<String, Object> arguments);
    }

    private final Map<String, String> types = new HashMap<>();        // "Order.restaurant" -> "Restaurant"
    private final Map<String, Resolver> resolvers = new HashMap<>();
    private final Map<String, BatchResolver> batchResolvers = new HashMap<>();
    private final Map<String, Integer> listDefaults = new HashMap<>();   // list fields and their default size
    private int resolvedFields;

    /** Declares the type of an object-valued field (lists included), so the engine can descend. */
    public Engine type(String typeAndField, String returnType) {
        types.put(typeAndField, returnType);
        return this;
    }

    /** Marks a field as a list; {@code defaultSize} is assumed when there is no {@code first} argument. */
    public Engine list(String typeAndField, int defaultSize) {
        listDefaults.put(typeAndField, defaultSize);
        return this;
    }

    /**
     * Static cost, computed before executing anything: every field costs 1 per expected instance,
     * and a list multiplies everything under it by its {@code first} argument (or default size).
     */
    public long complexity(Query query) {
        return cost(query.fields(), "Query", 1);
    }

    private long cost(List<Query.Field> fields, String type, long multiplier) {
        long total = 0;
        for (Query.Field f : fields) {
            String key = type + "." + f.name();
            total += multiplier;
            if (!f.isLeaf()) {
                long m = multiplier;
                Integer listSize = listDefaults.get(key);
                if (listSize != null) {
                    m *= f.arguments().get("first") instanceof Integer n ? n : listSize;
                }
                total += cost(f.selection(), types.get(key), m);
            }
        }
        return total;
    }

    public Engine resolver(String typeAndField, Resolver resolver) {
        resolvers.put(typeAndField, resolver);
        return this;
    }

    public Engine batchResolver(String typeAndField, BatchResolver resolver) {
        batchResolvers.put(typeAndField, resolver);
        return this;
    }

    /** Executes against the root object; returns {@code {"data": {...}}} as nested maps and lists. */
    public Map<String, Object> execute(Query query, Mode mode, Object root) {
        resolvedFields = 0;
        Map<String, Object> data = level(query.fields(), "Query", List.of(root), mode).getFirst();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        return response;
    }

    /** How many field values the last execution produced (a measure of work, like a cost). */
    public int resolvedFields() {
        return resolvedFields;
    }

    private List<Map<String, Object>> level(List<Query.Field> fields, String type, List<Object> parents, Mode mode) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (int p = 0; p < parents.size(); p++) {
            results.add(new LinkedHashMap<>());
        }
        for (Query.Field field : fields) {
            String key = type + "." + field.name();
            List<Object> values = resolve(key, field, parents, mode);
            resolvedFields += parents.size();
            if (field.isLeaf()) {
                for (int p = 0; p < parents.size(); p++) {
                    results.get(p).put(field.name(), values.get(p));
                }
                continue;
            }
            String childType = types.get(key);
            if (childType == null) {
                throw new IllegalArgumentException("unknown object field " + key);
            }
            // All children of this field, across all parents, form the next level.
            List<Object> children = new ArrayList<>();
            for (Object v : values) {
                if (v instanceof List<?> list) {
                    children.addAll(list);
                } else if (v != null) {
                    children.add(v);
                }
            }
            List<Map<String, Object>> childResults = children.isEmpty() ? List.of()
                    : level(field.selection(), childType, children, mode);
            int next = 0;
            for (int p = 0; p < parents.size(); p++) {
                Object v = values.get(p);
                Object out;
                if (v instanceof List<?> list) {
                    List<Object> items = new ArrayList<>();
                    for (int k = 0; k < list.size(); k++) {
                        items.add(childResults.get(next++));
                    }
                    out = items;
                } else if (v != null) {
                    out = childResults.get(next++);
                } else {
                    out = null;
                }
                results.get(p).put(field.name(), out);
            }
        }
        return results;
    }

    private List<Object> resolve(String key, Query.Field field, List<Object> parents, Mode mode) {
        BatchResolver batch = batchResolvers.get(key);
        if (mode == Mode.BATCHED && batch != null) {
            List<Object> values = batch.resolve(parents, field.arguments());
            if (values.size() != parents.size()) {
                throw new IllegalStateException(key + ": batch returned " + values.size() + " for " + parents.size());
            }
            return values;
        }
        Resolver single = resolvers.get(key);
        List<Object> values = new ArrayList<>(parents.size());
        for (Object parent : parents) {
            values.add(single != null ? single.resolve(parent, field.arguments()) : property(parent, field.name()));
        }
        return values;
    }

    private static Object property(Object parent, String name) {
        if (parent instanceof Record) {
            for (RecordComponent c : parent.getClass().getRecordComponents()) {
                if (c.getName().equals(name)) {
                    try {
                        Object v = c.getAccessor().invoke(parent);
                        return v instanceof Enum<?> e ? e.name() : v;
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }
        throw new IllegalArgumentException("no resolver or property for field '" + name + "' on " + parent);
    }
}
