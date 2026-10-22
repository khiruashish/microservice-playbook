import dev.foodfleet.ch53.container.ContainerLimits;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Prints the container's limits and the JVM's ergonomics, then keeps {@code args[0]} MiB of live data on the heap. */
public final class Alloc {

    public static void main(String[] args) throws Exception {
        System.out.println(ContainerLimits.describe(ContainerLimits.read(Path.of("/sys/fs/cgroup")), ContainerLimits.jvm()));
        int mib = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        List<byte[]> live = new ArrayList<>();
        for (int i = 0; i < mib; i++) {
            byte[] b = new byte[1024 * 1024];
            for (int k = 0; k < b.length; k += 4096) {
                b[k] = 1;                      // touch every page, so the memory is really used
            }
            live.add(b);
        }
        System.out.println("holding " + live.size() + " MiB");
    }
}
