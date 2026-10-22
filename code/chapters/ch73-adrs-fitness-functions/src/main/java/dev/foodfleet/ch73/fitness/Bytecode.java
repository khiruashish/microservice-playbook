package dev.foodfleet.ch73.fitness;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * What a release's compiled classes depend on and what they publish, read with the JDK's ClassFile API (JEP 484).
 * Dependencies are every class named in the constant pool or in a descriptor, as jdeps reads them; the published API is
 * the public and protected methods of public classes.
 */
public final class Bytecode {

    private static final Pattern TYPE = Pattern.compile("L([^;<]+)[;<]");

    /** Class name (dots) to the classes it depends on. */
    public final Map<String, Set<String>> dependencies = new TreeMap<>();
    /** Class name to its public members, as {@code name(descriptor)}. */
    public final Map<String, Set<String>> api = new TreeMap<>();

    public static Bytecode read(Path classes) throws IOException {
        Bytecode b = new Bytecode();
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                ClassModel cm = ClassFile.of().parse(Files.readAllBytes(f));
                String name = dots(cm.thisClass().asInternalName());
                Set<String> deps = new TreeSet<>();
                for (PoolEntry e : cm.constantPool()) {
                    if (e instanceof ClassEntry c) {
                        String n = c.asInternalName();
                        if (!n.startsWith("[")) {
                            deps.add(dots(n));
                        } else {
                            types(n, deps);
                        }
                    } else if (e instanceof NameAndTypeEntry nt) {
                        types(nt.type().stringValue(), deps);
                    } else if (e instanceof Utf8Entry u && u.stringValue().startsWith("(")) {
                        types(u.stringValue(), deps);
                    }
                }
                cm.fields().forEach(fm -> types(fm.fieldType().stringValue(), deps));
                deps.remove(name);
                b.dependencies.put(name, deps);
                if (cm.flags().has(AccessFlag.PUBLIC)) {
                    Set<String> members = new TreeSet<>();
                    for (MethodModel m : cm.methods()) {
                        if (m.flags().has(AccessFlag.PUBLIC) || m.flags().has(AccessFlag.PROTECTED)) {
                            members.add(m.methodName().stringValue() + m.methodType().stringValue());
                        }
                    }
                    b.api.put(name, members);
                }
            }
        }
        return b;
    }

    private static void types(String descriptor, Set<String> into) {
        Matcher m = TYPE.matcher(descriptor);
        while (m.find()) {
            into.add(dots(m.group(1)));
        }
    }

    private static String dots(String internal) {
        return internal.replace('/', '.');
    }
}
