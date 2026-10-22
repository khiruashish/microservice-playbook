package dev.foodfleet.ch67.memory;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a JVM's memory goes besides the heap: Native Memory Tracking's summary ({@code jcmd <pid> VM.native_memory
 * summary}), asked from inside the process through the DiagnosticCommand MBean. Needs
 * {@code -XX:NativeMemoryTracking=summary}.
 */
public final class NativeMemory {

    private NativeMemory() {
    }

    public static String summary() throws Exception {
        return (String) ManagementFactory.getPlatformMBeanServer().invoke(new ObjectName("com.sun.management:type=DiagnosticCommand"), "vmNativeMemory",
                new Object[] {new String[] {"summary", "scale=MB"}}, new String[] {String[].class.getName()});
    }

    static final Pattern CATEGORY = Pattern.compile("^-\\s+(.+?) \\(reserved=\\d+MB, committed=(\\d+)MB\\)", Pattern.MULTILINE);
    static final Pattern TOTAL = Pattern.compile("^Total: reserved=\\d+MB, committed=(\\d+)MB", Pattern.MULTILINE);

    /** Committed megabytes per category, plus "Total", from a summary. */
    public static Map<String, Long> committed(String summary) {
        Map<String, Long> out = new LinkedHashMap<>();
        Matcher m = CATEGORY.matcher(summary);
        while (m.find()) {
            out.put(m.group(1).trim(), Long.parseLong(m.group(2)));
        }
        Matcher t = TOTAL.matcher(summary);
        if (t.find()) {
            out.put("Total", Long.parseLong(t.group(1)));
        }
        return out;
    }
}
