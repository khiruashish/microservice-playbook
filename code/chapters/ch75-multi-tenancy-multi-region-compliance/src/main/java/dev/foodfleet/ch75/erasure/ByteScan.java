package dev.foodfleet.ch75.erasure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Counts occurrences of a byte string in every regular file under a directory: where does erased data survive? */
public final class ByteScan {

    private ByteScan() {
    }

    public static long count(Path root, byte[] needle) throws IOException {
        if (!Files.exists(root)) {
            return 0;
        }
        long n = 0;
        try (Stream<Path> s = Files.walk(root)) {
            for (Path f : s.filter(Files::isRegularFile).toList()) {
                n += count(Files.readAllBytes(f), needle);
            }
        }
        return n;
    }

    public static long count(byte[] hay, byte[] needle) {
        long n = 0;
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    continue outer;
                }
            }
            n++;
        }
        return n;
    }
}
