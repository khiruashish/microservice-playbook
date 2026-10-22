package dev.foodfleet.ch31.reads;

/**
 * A position in the primary's write-ahead log, as PostgreSQL prints it ({@code 16/B374D848}: two hexadecimal
 * halves of a 64-bit number). A client that wrote gets the primary's position after its write; a replica that
 * has replayed at least that far shows the write. Positions only grow, so "newest of two" is just the larger.
 */
public record ConsistencyToken(long position) implements Comparable<ConsistencyToken> {

    public static ConsistencyToken parse(String lsn) {
        int slash = lsn.indexOf('/');
        if (slash < 1 || slash == lsn.length() - 1) {
            throw new IllegalArgumentException("not a WAL position: " + lsn);
        }
        long hi = Long.parseLong(lsn.substring(0, slash), 16);
        long lo = Long.parseLong(lsn.substring(slash + 1), 16);
        if (hi > 0xFFFFFFFFL || lo > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("not a WAL position: " + lsn);
        }
        return new ConsistencyToken((hi << 32) | lo);
    }

    public boolean isAtLeast(ConsistencyToken other) {
        return Long.compareUnsigned(position, other.position) >= 0;
    }

    public ConsistencyToken newest(ConsistencyToken other) {
        return isAtLeast(other) ? this : other;
    }

    @Override
    public int compareTo(ConsistencyToken other) {
        return Long.compareUnsigned(position, other.position);
    }

    @Override
    public String toString() {
        return Long.toHexString(position >>> 32).toUpperCase() + "/" + Long.toHexString(position & 0xFFFFFFFFL).toUpperCase();
    }
}
