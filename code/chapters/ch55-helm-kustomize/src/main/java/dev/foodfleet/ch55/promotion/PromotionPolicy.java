package dev.foodfleet.ch55.promotion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks rendered manifests (the output of {@code helm template} or {@code kustomize build}) before they're applied:
 * outside dev every image is pinned by digest, and production runs exactly the digests staging tested. Run in CI on
 * every change to an environment's values or overlay.
 */
public final class PromotionPolicy {

    private static final Pattern IMAGE = Pattern.compile("(?m)^\\s*-?\\s*image:\\s*\"?([^\"\\s]+)\"?\\s*$");

    private PromotionPolicy() {
    }

    /** Every {@code image:} in the rendered YAML, in order. */
    public static List<ImageRef> images(String renderedYaml) {
        List<ImageRef> refs = new ArrayList<>();
        Matcher m = IMAGE.matcher(renderedYaml);
        while (m.find()) {
            refs.add(ImageRef.parse(m.group(1)));
        }
        return refs;
    }

    /** Problems with one environment's manifests; empty when it may be applied. */
    public static List<String> check(String environment, String renderedYaml) {
        List<String> problems = new ArrayList<>();
        if (!environment.equals("dev")) {
            for (ImageRef r : images(renderedYaml)) {
                if (!r.pinned()) {
                    problems.add(environment + ": " + r + " is not pinned by digest");
                }
            }
        }
        return problems;
    }

    /** Problems promoting from {@code from} to {@code to}: every repository must run the same digest in both. */
    public static List<String> samePromoted(String from, String fromYaml, String to, String toYaml) {
        Map<String, String> tested = new LinkedHashMap<>();
        images(fromYaml).forEach(r -> tested.put(r.repository(), r.digest()));
        List<String> problems = new ArrayList<>();
        for (ImageRef r : images(toYaml)) {
            String d = tested.get(r.repository());
            if (d == null) {
                problems.add(to + ": " + r.repository() + " isn't in " + from);
            } else if (!d.equals(r.digest())) {
                problems.add(to + ": " + r.repository() + " runs " + r.digest() + ", " + from + " tested " + d);
            }
        }
        return problems;
    }

    /**
     * The pipeline's only edit to an overlay: set the digest of {@code repository} in a kustomization.yaml's
     * {@code images:} list ({@code kustomize edit set image} does the same).
     */
    public static String setDigest(String kustomization, String repository, String sha256) {
        ImageRef.parse(repository + "@" + sha256);                // validates both
        Pattern entry = Pattern.compile("(?m)(-\\s*name:\\s*" + Pattern.quote(repository) + "\\s*\\n\\s*digest:\\s*)\\S+");
        Matcher m = entry.matcher(kustomization);
        if (!m.find()) {
            throw new IllegalArgumentException("no images entry for " + repository);
        }
        return m.replaceFirst(Matcher.quoteReplacement(m.group(1) + sha256));
    }
}
