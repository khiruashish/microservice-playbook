package dev.foodfleet.ch35.orders;

/**
 * Bounds on what one request may ask for (API4: unrestricted resource consumption). Without them one call with
 * size=1000000 makes the service build, serialise and send everything it may see; limits are checked before any
 * work is done.
 */
public record PageLimits(int defaultSize, int maxSize, int maxOffset) {

    public static final PageLimits DEFAULT = new PageLimits(20, 100, 10_000);

    public int size(Integer requested) {
        if (requested == null) {
            return defaultSize;
        }
        if (requested < 1 || requested > maxSize) {
            throw new BadRequestException("size must be between 1 and " + maxSize);
        }
        return requested;
    }

    /** Deep offsets are expensive and are how scrapers walk everything; past this, use a filter or a cursor. */
    public int offset(Integer page, int size) {
        int p = page == null ? 0 : page;
        if (p < 0 || (long) p * size > maxOffset) {
            throw new BadRequestException("page out of range (at most " + maxOffset + " rows deep)");
        }
        return p * size;
    }
}
