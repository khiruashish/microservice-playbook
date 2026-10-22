package dev.foodfleet.ch66.pinning;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 1,000 virtual threads, each blocking for 50 ms while holding its own lock, so there's no contention at all: the
 * only question is whether a virtual thread blocked inside {@code synchronized} lets go of its carrier thread. On
 * JDK 21 it doesn't ("pinning"), so at most as many block at once as there are carriers; JDK 24 fixed it (JEP 491).
 * Usage: {@code Pinning synchronized|reentrantlock|none}. Compiled for Java 21.
 */
public final class Pinning {

    private Pinning() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        int n = 1000;
        long t = System.nanoTime();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Object monitor = new Object();
            ReentrantLock lock = new ReentrantLock();
            threads.add(Thread.ofVirtual().start(() -> {
                try {
                    switch (mode) {
                        case "synchronized" -> {
                            synchronized (monitor) {
                                Thread.sleep(50);
                            }
                        }
                        case "reentrantlock" -> {
                            lock.lock();
                            try {
                                Thread.sleep(50);
                            } finally {
                                lock.unlock();
                            }
                        }
                        default -> Thread.sleep(50);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        for (Thread th : threads) {
            th.join();
        }
        double s = (System.nanoTime() - t) / 1e9;
        System.out.printf("%s %s: %d tasks of 50 ms in %.2f s (%.0f tasks/s; %d carriers)%n", System.getProperty("java.version"), mode, n, s, n / s,
                Runtime.getRuntime().availableProcessors());
    }
}
