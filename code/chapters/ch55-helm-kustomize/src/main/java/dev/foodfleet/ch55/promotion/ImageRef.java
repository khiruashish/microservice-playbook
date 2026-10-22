package dev.foodfleet.ch55.promotion;

import java.util.regex.Pattern;

/**
 * A container image reference: {@code registry/repository[:tag][@sha256:digest]}. A tag is a pointer anyone with push
 * rights can move; a digest names exact content. What runs in staging and production is named by digest.
 */
public record ImageRef(String repository, String tag, String digest) {

    private static final Pattern DIGEST = Pattern.compile("sha256:[a-f0-9]{64}");
    private static final Pattern TAG = Pattern.compile("[\\w][\\w.-]{0,127}");

    public static ImageRef parse(String ref) {
        String s = ref.trim();
        String digest = null;
        int at = s.indexOf('@');
        if (at >= 0) {
            digest = s.substring(at + 1);
            s = s.substring(0, at);
            if (!DIGEST.matcher(digest).matches()) {
                throw new IllegalArgumentException("not a sha256 digest in " + ref);
            }
        }
        String tag = null;
        int colon = s.lastIndexOf(':');
        if (colon > s.lastIndexOf('/')) {                     // "registry:5000/repo" has its colon before the last slash
            tag = s.substring(colon + 1);
            s = s.substring(0, colon);
            if (!TAG.matcher(tag).matches()) {
                throw new IllegalArgumentException("not a tag in " + ref);
            }
        }
        if (s.isEmpty() || s.contains(" ")) {
            throw new IllegalArgumentException("not an image reference: " + ref);
        }
        return new ImageRef(s, tag, digest);
    }

    public boolean pinned() {
        return digest != null;
    }

    /** The same image, named by its digest only. */
    public ImageRef pinTo(String sha256) {
        if (!sha256.matches("sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException("not a sha256 digest: " + sha256);
        }
        return new ImageRef(repository, null, sha256);
    }

    @Override
    public String toString() {
        return repository + (tag != null ? ":" + tag : "") + (digest != null ? "@" + digest : "");
    }
}
