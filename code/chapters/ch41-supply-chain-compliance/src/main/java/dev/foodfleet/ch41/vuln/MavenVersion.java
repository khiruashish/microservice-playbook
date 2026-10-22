package dev.foodfleet.ch41.vuln;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maven's version ordering (the classic {@code ComparableVersion} algorithm): "2.0-beta9" &lt; "2.0-rc1" &lt; "2.0" =
 * "2.0.0" = "2.0.Final" &lt; "2.0-sp1" &lt; "2.0.1". A scanner that compares versions as strings, or as dotted numbers
 * only, gets these wrong in both directions: "2.10" sorts before "2.9", "6.0.0.Final" looks newer than "6.0.0".
 * <p>
 * Items are numbers, qualifiers or sub-lists (after '-' or a digit/letter switch). Known qualifiers sort
 * alpha &lt; beta &lt; milestone &lt; rc (= cr) &lt; snapshot &lt; release (= ga = final = "") &lt; sp; unknown qualifiers
 * sort after all of them, alphabetically, and every qualifier sorts before a number.
 */
public final class MavenVersion implements Comparable<MavenVersion> {

    private sealed interface Item permits IntItem, StringItem, ListItem {
        int compareTo(Item other);   // other may be null: "padding" when one list is shorter

        boolean isNull();
    }

    private record IntItem(java.math.BigInteger value) implements Item {
        static final IntItem ZERO = new IntItem(java.math.BigInteger.ZERO);

        public int compareTo(Item other) {
            if (other == null) {
                return value.signum() == 0 ? 0 : 1;
            }
            return switch (other) {
                case IntItem i -> value.compareTo(i.value);
                case StringItem s -> 1;
                case ListItem l -> 1;
            };
        }

        public boolean isNull() {
            return value.signum() == 0;
        }
    }

    private static final List<String> QUALIFIERS = List.of("alpha", "beta", "milestone", "rc", "snapshot", "", "sp");
    private static final Map<String, String> ALIASES = Map.of("ga", "", "final", "", "release", "", "cr", "rc");
    private static final String RELEASE_INDEX = String.valueOf(QUALIFIERS.indexOf(""));

    private record StringItem(String value) implements Item {
        static StringItem of(String raw, boolean followedByDigit) {
            String v = raw;
            if (followedByDigit && v.length() == 1) {
                v = switch (v.charAt(0)) {
                    case 'a' -> "alpha";
                    case 'b' -> "beta";
                    case 'm' -> "milestone";
                    default -> v;
                };
            }
            return new StringItem(ALIASES.getOrDefault(v, v));
        }

        static String comparable(String q) {
            int i = QUALIFIERS.indexOf(q);
            return i == -1 ? QUALIFIERS.size() + "-" + q : String.valueOf(i);
        }

        public int compareTo(Item other) {
            if (other == null) {
                return comparable(value).compareTo(RELEASE_INDEX);
            }
            return switch (other) {
                case IntItem i -> -1;
                case StringItem s -> comparable(value).compareTo(comparable(s.value));
                case ListItem l -> -1;
            };
        }

        public boolean isNull() {
            return comparable(value).equals(RELEASE_INDEX);
        }
    }

    private static final class ListItem extends ArrayList<Item> implements Item {
        private static final long serialVersionUID = 1L;

        public int compareTo(Item other) {
            if (other == null) {
                return isEmpty() ? 0 : get(0).compareTo(null);
            }
            return switch (other) {
                case IntItem i -> -1;
                case StringItem s -> 1;
                case ListItem l -> {
                    for (int k = 0; k < Math.max(size(), l.size()); k++) {
                        Item a = k < size() ? get(k) : null;
                        Item b = k < l.size() ? l.get(k) : null;
                        int r = a == null ? (b == null ? 0 : -b.compareTo(null)) : a.compareTo(b);
                        if (r != 0) {
                            yield r;
                        }
                    }
                    yield 0;
                }
            };
        }

        public boolean isNull() {
            return isEmpty();
        }

        void normalize() {
            for (int i = size() - 1; i >= 0; i--) {
                Item item = get(i);
                if (item.isNull()) {
                    remove(i);
                } else if (!(item instanceof ListItem)) {
                    break;
                }
            }
        }

        @Override
        public boolean equals(Object o) {
            return this == o;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    private final String raw;
    private final ListItem items;

    private MavenVersion(String raw, ListItem items) {
        this.raw = raw;
        this.items = items;
    }

    public static MavenVersion parse(String version) {
        String v = version.toLowerCase(Locale.ROOT);
        ListItem root = new ListItem();
        ListItem list = root;
        Deque<ListItem> stack = new ArrayDeque<>();
        stack.push(list);
        boolean isDigit = false;
        int start = 0;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '.') {
                list.add(i == start ? IntItem.ZERO : item(isDigit, v.substring(start, i)));
                start = i + 1;
            } else if (c == '-') {
                list.add(i == start ? IntItem.ZERO : item(isDigit, v.substring(start, i)));
                start = i + 1;
                ListItem sub = new ListItem();
                list.add(sub);
                list = sub;
                stack.push(list);
            } else if (Character.isDigit(c)) {
                if (!isDigit && i > start) {
                    list.add(StringItem.of(v.substring(start, i), true));
                    start = i;
                    ListItem sub = new ListItem();
                    list.add(sub);
                    list = sub;
                    stack.push(list);
                }
                isDigit = true;
            } else {
                if (isDigit && i > start) {
                    list.add(item(true, v.substring(start, i)));
                    start = i;
                    ListItem sub = new ListItem();
                    list.add(sub);
                    list = sub;
                    stack.push(list);
                }
                isDigit = false;
            }
        }
        if (v.length() > start) {
            list.add(item(isDigit, v.substring(start)));
        }
        while (!stack.isEmpty()) {
            stack.pop().normalize();
        }
        return new MavenVersion(version, root);
    }

    private static Item item(boolean isDigit, String s) {
        return isDigit ? new IntItem(new java.math.BigInteger(s)) : StringItem.of(s, false);
    }

    @Override
    public int compareTo(MavenVersion o) {
        return items.compareTo(o.items);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MavenVersion m && compareTo(m) == 0;
    }

    @Override
    public int hashCode() {
        return 0;   // equal versions can have different spellings ("1.0" and "1"); not meant as a hash key
    }

    @Override
    public String toString() {
        return raw;
    }
}
