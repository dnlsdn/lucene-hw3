package it.uniroma3.idd.hw3;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Evaluation measures from the IR part of the course (Manning et al., ch. 8).
 * A ranking is the list of retrieved document ids, best first.
 */
public final class Metrics {

    private Metrics() {}

    public static double precisionAt(List<Integer> ranking, Set<Integer> relevant, int k) {
        int hits = 0;
        for (int i = 0; i < Math.min(k, ranking.size()); i++) {
            if (relevant.contains(ranking.get(i))) hits++;
        }
        return (double) hits / k;
    }

    public static double recallAt(List<Integer> ranking, Set<Integer> relevant, int k) {
        if (relevant.isEmpty()) return 0;
        int hits = 0;
        for (int i = 0; i < Math.min(k, ranking.size()); i++) {
            if (relevant.contains(ranking.get(i))) hits++;
        }
        return (double) hits / relevant.size();
    }

    /** Precision at rank R, where R is the number of relevant documents. */
    public static double rPrecision(List<Integer> ranking, Set<Integer> relevant) {
        return relevant.isEmpty() ? 0 : precisionAt(ranking, relevant, relevant.size());
    }

    /** Average precision: mean of the precision at the rank of each relevant document (0 if not retrieved). */
    public static double averagePrecision(List<Integer> ranking, Set<Integer> relevant) {
        if (relevant.isEmpty()) return 0;
        int hits = 0;
        double sum = 0;
        for (int i = 0; i < ranking.size(); i++) {
            if (relevant.contains(ranking.get(i))) {
                hits++;
                sum += (double) hits / (i + 1);
            }
        }
        return sum / relevant.size();
    }

    public static double reciprocalRank(List<Integer> ranking, Set<Integer> relevant) {
        for (int i = 0; i < ranking.size(); i++) {
            if (relevant.contains(ranking.get(i))) return 1.0 / (i + 1);
        }
        return 0;
    }

    /**
     * nDCG@k with graded relevance. Cranfield codes go from 1 (complete answer)
     * to 4 (minimum interest): the gain is 5 - code, so 4 for the best documents.
     */
    public static double ndcgAt(List<Integer> ranking, Map<Integer, Integer> grades, int k) {
        double dcg = 0;
        for (int i = 0; i < Math.min(k, ranking.size()); i++) {
            Integer code = grades.get(ranking.get(i));
            if (code != null) dcg += (5 - code) / log2(i + 2);
        }
        double idcg = 0;
        List<Integer> ideal = grades.values().stream().map(c -> 5 - c).sorted((a, b) -> b - a).toList();
        for (int i = 0; i < Math.min(k, ideal.size()); i++) {
            idcg += ideal.get(i) / log2(i + 2);
        }
        return idcg == 0 ? 0 : dcg / idcg;
    }

    /** Interpolated precision at the 11 recall levels 0.0, 0.1, ..., 1.0. */
    public static double[] elevenPoint(List<Integer> ranking, Set<Integer> relevant) {
        double[] points = new double[11];
        if (relevant.isEmpty()) return points;
        int n = ranking.size();
        double[] precision = new double[n];
        double[] recall = new double[n];
        int hits = 0;
        for (int i = 0; i < n; i++) {
            if (relevant.contains(ranking.get(i))) hits++;
            precision[i] = (double) hits / (i + 1);
            recall[i] = (double) hits / relevant.size();
        }
        for (int level = 0; level <= 10; level++) {
            double r = level / 10.0;
            double best = 0;
            for (int i = 0; i < n; i++) {
                if (recall[i] >= r - 1e-9) best = Math.max(best, precision[i]);
            }
            points[level] = best; // highest precision at any recall >= r
        }
        return points;
    }

    /** Precision, recall and F1 of an unranked set of results. */
    public record SetScores(double precision, double recall, double f1) {}

    public static SetScores setScores(Set<Integer> retrieved, Set<Integer> relevant) {
        long tp = retrieved.stream().filter(relevant::contains).count();
        double p = retrieved.isEmpty() ? 0 : (double) tp / retrieved.size();
        double r = relevant.isEmpty() ? 0 : (double) tp / relevant.size();
        double f1 = p + r == 0 ? 0 : 2 * p * r / (p + r);
        return new SetScores(p, r, f1);
    }

    /**
     * Two-sided paired randomization test on per-query scores: how often a random
     * swap of the two systems' scores gives a mean difference at least as large as
     * the observed one.
     */
    public static double pairedRandomizationTest(double[] a, double[] b, int trials, long seed) {
        int n = a.length;
        double observed = Math.abs(meanDiff(a, b, null));
        Random random = new Random(seed);
        boolean[] swap = new boolean[n];
        int extreme = 0;
        for (int t = 0; t < trials; t++) {
            for (int i = 0; i < n; i++) swap[i] = random.nextBoolean();
            if (Math.abs(meanDiff(a, b, swap)) >= observed - 1e-12) extreme++;
        }
        return (extreme + 1.0) / (trials + 1.0);
    }

    private static double meanDiff(double[] a, double[] b, boolean[] swap) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += swap != null && swap[i] ? -d : d;
        }
        return sum / a.length;
    }

    public static double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return values.length == 0 ? 0 : sum / values.length;
    }

    public static double std(double[] values) {
        if (values.length < 2) return 0;
        double m = mean(values), sum = 0;
        for (double v : values) sum += (v - m) * (v - m);
        return Math.sqrt(sum / (values.length - 1));
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}
