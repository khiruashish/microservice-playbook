package dev.foodfleet.ch27.compat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * A response or event schema as a tree of fields: what a structural diff tool (oasdiff for OpenAPI,
 * a schema registry for Avro) sees. {@code meaning} is what a field's value signifies; it is not in
 * any schema language, which is exactly why changes to it are invisible to tools.
 */
public record Schema(Map<String, Field> fields) {

    public enum Type { STRING, INT, ENUM, OBJECT }

    public record Field(Type type, boolean alwaysPresent, List<String> enumValues, Schema object, String meaning) {

        public static Field string(String meaning) {
            return new Field(Type.STRING, true, List.of(), null, meaning);
        }

        public static Field integer(String meaning) {
            return new Field(Type.INT, true, List.of(), null, meaning);
        }

        public static Field enumeration(List<String> values) {
            return new Field(Type.ENUM, true, List.copyOf(values), null, "");
        }

        public static Field object(Schema schema) {
            return new Field(Type.OBJECT, true, List.of(), schema, "");
        }

        public Field optional() {
            return new Field(type, false, enumValues, object, meaning);
        }

        public Field withType(Type t) {
            return new Field(t, alwaysPresent, enumValues, object, meaning);
        }

        public Field withEnumValues(List<String> values) {
            return new Field(type, alwaysPresent, List.copyOf(values), object, meaning);
        }

        public Field withMeaning(String m) {
            return new Field(type, alwaysPresent, enumValues, object, m);
        }

        public Field withObject(Schema s) {
            return new Field(type, alwaysPresent, enumValues, s, meaning);
        }
    }

    public static Schema of(Object... nameFieldPairs) {
        Map<String, Field> fields = new LinkedHashMap<>();
        for (int i = 0; i < nameFieldPairs.length; i += 2) {
            fields.put((String) nameFieldPairs[i], (Field) nameFieldPairs[i + 1]);
        }
        return new Schema(fields);
    }

    public Schema with(String name, Field field) {
        Map<String, Field> copy = new LinkedHashMap<>(fields);
        copy.put(name, field);
        return new Schema(copy);
    }

    public Schema without(String name) {
        Map<String, Field> copy = new LinkedHashMap<>(fields);
        copy.remove(name);
        return new Schema(copy);
    }

    public Schema change(String name, UnaryOperator<Field> change) {
        return with(name, change.apply(fields.get(name)));
    }

    /** Field at a dotted path such as {@code restaurant.area}, or null. */
    public Field at(String path) {
        int dot = path.indexOf('.');
        if (dot < 0) {
            return fields.get(path);
        }
        Field parent = fields.get(path.substring(0, dot));
        return parent == null || parent.object() == null ? null : parent.object().at(path.substring(dot + 1));
    }
}
