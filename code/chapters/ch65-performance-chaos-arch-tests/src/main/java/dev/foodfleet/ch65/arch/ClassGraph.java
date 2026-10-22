package dev.foodfleet.ch65.arch;

import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.classfile.constantpool.PoolEntry;
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
 * The classes in a directory (or a module of the running JDK) and what each depends on, read from bytecode with the
 * JDK's ClassFile API, in one of two ways:
 * <ul>
 *   <li>{@link Mode#CODE}, as ArchUnit's importer does with an ASM visitor: the superclass and interfaces, field and
 *       method types, generic signatures, and the classes the code actually uses (field accesses, calls, {@code new},
 *       casts, {@code instanceof}, class literals)</li>
 *   <li>{@link Mode#CONSTANT_POOL}, as jdeps does: every class named anywhere in the constant pool, including the ones
 *       javac records for constants it inlined</li>
 * </ul>
 * Names in strings (reflection) aren't dependencies in either.
 */
public final class ClassGraph {

    public record JavaClass(String name, Set<String> dependencies, Set<String> accesses) {
        public String packageName() {
            int dot = name.lastIndexOf('.');
            return dot < 0 ? "" : name.substring(0, dot);
        }

        public String simpleName() {
            return name.substring(name.lastIndexOf('.') + 1);
        }
    }

    public enum Mode { CODE, CONSTANT_POOL }

    private static final Pattern TYPE = Pattern.compile("L([^;<]+)[;<]");

    private final Map<String, JavaClass> classes = new TreeMap<>();

    public Map<String, JavaClass> classes() {
        return classes;
    }

    public static ClassGraph of(Path root, String prefix) throws IOException {
        return of(root, prefix, Mode.CODE);
    }

    public static ClassGraph of(Path root, String prefix, Mode mode) throws IOException {
        ClassGraph g = new ClassGraph();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                JavaClass c = read(Files.readAllBytes(f), mode);
                if (c.name().startsWith(prefix) && !c.name().endsWith("package-info") && !c.name().endsWith("module-info")) {
                    g.classes.put(c.name(), c);
                }
            }
        }
        return g;
    }

    static JavaClass read(byte[] bytes, Mode mode) {
        ClassModel cm = ClassFile.of().parse(bytes);
        String self = cm.thisClass().asInternalName().replace('/', '.');
        Set<String> deps = new TreeSet<>();
        Set<String> accesses = new TreeSet<>();
        for (PoolEntry e : cm.constantPool()) {
            if (mode == Mode.CONSTANT_POOL) {
                switch (e) {
                    case ClassEntry ce -> type(ce.asInternalName(), deps);
                    case NameAndTypeEntry nt -> descriptor(nt.type().stringValue(), deps);
                    default -> {
                    }
                }
            }
            if (e instanceof MemberRefEntry m) {
                accesses.add(m.owner().asInternalName().replace('/', '.') + "." + m.name().stringValue() + (e instanceof FieldRefEntry ? "" : "()"));
            }
        }
        cm.superclass().ifPresent(sc -> type(sc.asInternalName(), deps));
        cm.interfaces().forEach(i -> type(i.asInternalName(), deps));
        for (FieldModel f : cm.fields()) {
            descriptor(f.fieldType().stringValue(), deps);
            f.findAttribute(Attributes.signature()).ifPresent(s -> descriptor(s.signature().stringValue(), deps));
        }
        for (MethodModel m : cm.methods()) {
            descriptor(m.methodType().stringValue(), deps);
            m.findAttribute(Attributes.signature()).ifPresent(s -> descriptor(s.signature().stringValue(), deps));
            m.findAttribute(Attributes.exceptions()).ifPresent(x -> x.exceptions().forEach(c -> type(c.asInternalName(), deps)));
            m.code().ifPresent(code -> code(code, deps));
        }
        cm.findAttribute(Attributes.signature()).ifPresent(s -> descriptor(s.signature().stringValue(), deps));
        deps.remove(self);
        return new JavaClass(self, deps, accesses);
    }

    /** The classes the code uses: what an ASM visitor's visitFieldInsn, visitMethodInsn, visitTypeInsn and visitLdcInsn see. */
    static void code(CodeModel code, Set<String> deps) {
        code.forEach(element -> {
            switch (element) {
                case FieldInstruction f -> {
                    type(f.owner().asInternalName(), deps);
                    descriptor(f.typeSymbol().descriptorString(), deps);
                }
                case InvokeInstruction i -> {
                    type(i.owner().asInternalName(), deps);
                    descriptor(i.typeSymbol().descriptorString(), deps);
                }
                case NewObjectInstruction n -> type(n.className().asInternalName(), deps);
                case NewReferenceArrayInstruction n -> type(n.componentType().asInternalName(), deps);
                case TypeCheckInstruction t -> type(t.type().asInternalName(), deps);
                case ConstantInstruction.LoadConstantInstruction l when l.constantEntry() instanceof ClassEntry ce -> type(ce.asInternalName(), deps);
                default -> {
                }
            }
        });
    }

    static void type(String internal, Set<String> out) {
        if (internal.startsWith("[")) {
            descriptor(internal, out);
        } else {
            out.add(internal.replace('/', '.'));
        }
    }

    static void descriptor(String d, Set<String> out) {
        Matcher m = TYPE.matcher(d);
        while (m.find()) {
            out.add(m.group(1).replace('/', '.'));
        }
    }
}
