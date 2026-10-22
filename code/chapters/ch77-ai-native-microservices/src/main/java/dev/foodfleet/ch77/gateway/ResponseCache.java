package dev.foodfleet.ch77.gateway;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Three ways to reuse an answer instead of calling the model: by the exact prompt, by a normalised prompt (case,
 * punctuation and spacing ignored), and by similarity (cosine over TF-IDF weighted words and word pairs, a stand-in for an
 * embedding model) above a threshold. Similarity finds more reuse, and some of it is wrong: a cached answer to a
 * different question.
 */
public final class ResponseCache {

    public enum Mode { EXACT, NORMALISED, SIMILAR }

    public record Hit(String key, String answer, double score) {
    }

    private final Mode mode;
    private final double threshold;
    private final Map<String, String> byKey = new HashMap<>();
    private final List<Map<String, Double>> vectors = new ArrayList<>();
    private final List<String> answers = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    private final Map<String, Integer> documentFrequency;
    private final int documents;

    /** @param corpus questions used to weight words (IDF); for SIMILAR only */
    public ResponseCache(Mode mode, double threshold, List<String> corpus) {
        this.mode = mode;
        this.threshold = threshold;
        documentFrequency = new HashMap<>();
        for (String q : corpus) {
            terms(q).keySet().forEach(t -> documentFrequency.merge(t, 1, Integer::sum));
        }
        documents = corpus.size();
    }

    public static String normalise(String prompt) {
        String s = Normalizer.normalize(prompt, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return s.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    static Map<String, Integer> terms(String prompt) {
        String[] w = normalise(prompt).split(" ");
        Map<String, Integer> t = new HashMap<>();
        for (int i = 0; i < w.length; i++) {
            t.merge(w[i], 1, Integer::sum);
            if (i + 1 < w.length) {
                t.merge(w[i] + " " + w[i + 1], 1, Integer::sum);
            }
        }
        return t;
    }

    Map<String, Double> vector(String prompt) {
        Map<String, Double> v = new HashMap<>();
        double norm = 0;
        for (var e : terms(prompt).entrySet()) {
            double idf = Math.log((documents + 1.0) / (documentFrequency.getOrDefault(e.getKey(), 0) + 1.0)) + 1;
            double x = e.getValue() * idf;
            v.put(e.getKey(), x);
            norm += x * x;
        }
        double n = Math.sqrt(norm);
        v.replaceAll((k, x) -> x / n);
        return v;
    }

    public Hit get(String prompt) {
        if (mode != Mode.SIMILAR) {
            String k = mode == Mode.EXACT ? prompt : normalise(prompt);
            String a = byKey.get(k);
            return a == null ? null : new Hit(k, a, 1);
        }
        Map<String, Double> q = vector(prompt);
        int best = -1;
        double bestScore = 0;
        for (int i = 0; i < vectors.size(); i++) {
            double s = 0;
            for (var e : q.entrySet()) {
                s += e.getValue() * vectors.get(i).getOrDefault(e.getKey(), 0.0);
            }
            if (s > bestScore) {
                bestScore = s;
                best = i;
            }
        }
        return best >= 0 && bestScore >= threshold ? new Hit(keys.get(best), answers.get(best), bestScore) : null;
    }

    public void put(String prompt, String answer) {
        if (mode == Mode.SIMILAR) {
            vectors.add(vector(prompt));
            answers.add(answer);
            keys.add(prompt);
        } else {
            byKey.put(mode == Mode.EXACT ? prompt : normalise(prompt), answer);
        }
    }
}
