package com.fraudshield.risk.model;

import com.fraudshield.risk.feature.FeatureVector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Trains a logistic-regression model with full-batch gradient descent, L2 regularisation and
 * cost-sensitive class weighting. Fraud is rare (about 2% of traffic); weighting each fraud
 * sample by sqrt(legit/fraud) rather than the full ratio lifts recall without flooding the
 * review queue with false positives, the trade-off every payments risk team has to make.
 * Evaluated on a stratified 80/20 hold-out set.
 */
public final class Trainer {

    private final int epochs;
    private final double learningRate;
    private final double l2;
    private final long seed;

    public Trainer(int epochs, double learningRate, double l2, long seed) {
        this.epochs = epochs;
        this.learningRate = learningRate;
        this.l2 = l2;
        this.seed = seed;
    }

    public static Trainer defaults() {
        return new Trainer(600, 0.5, 1e-3, 42);
    }

    public Result train(List<TrainingSample> samples, LogisticModel warmStart, String version, double threshold) {
        List<TrainingSample> fraud = new ArrayList<>();
        List<TrainingSample> legit = new ArrayList<>();
        for (TrainingSample s : samples) {
            (s.label() == 1 ? fraud : legit).add(s);
        }
        if (fraud.size() < 10 || legit.size() < 10) {
            throw new IllegalStateException("Need at least 10 fraud and 10 legit samples, have "
                    + fraud.size() + " fraud / " + legit.size() + " legit");
        }
        Random rnd = new Random(seed);
        Collections.shuffle(fraud, rnd);
        Collections.shuffle(legit, rnd);
        List<TrainingSample> train = new ArrayList<>();
        List<TrainingSample> test = new ArrayList<>();
        split(fraud, train, test);
        split(legit, train, test);

        long positives = train.stream().filter(s -> s.label() == 1).count();
        double posWeight = Math.sqrt((double) (train.size() - positives) / positives);
        double negWeight = 1.0;

        int dims = FeatureVector.SIZE;
        double[] w = warmStart.weights().clone();
        double b = warmStart.bias();
        for (int epoch = 0; epoch < epochs; epoch++) {
            double[] grad = new double[dims];
            double gradB = 0;
            double totalWeight = 0;
            for (TrainingSample s : train) {
                double z = b;
                for (int i = 0; i < dims; i++) z += w[i] * s.x().values()[i];
                double err = LogisticModel.sigmoid(z) - s.label();
                double cw = s.label() == 1 ? posWeight : negWeight;
                for (int i = 0; i < dims; i++) grad[i] += cw * err * s.x().values()[i];
                gradB += cw * err;
                totalWeight += cw;
            }
            for (int i = 0; i < dims; i++) {
                w[i] -= learningRate * (grad[i] / totalWeight + l2 * w[i]);
            }
            b -= learningRate * gradB / totalWeight;
        }

        LogisticModel candidate = new LogisticModel(version, b, w, Instant.now(), Map.of());
        LogisticModel evaluated = new LogisticModel(version, b, w, Instant.now(),
                evaluate(candidate, test, threshold, train.size()));
        return new Result(evaluated, test);
    }

    /** The trained model plus the hold-out set it was evaluated on. */
    public record Result(LogisticModel model, List<TrainingSample> holdout) {
    }

    private static void split(List<TrainingSample> src, List<TrainingSample> train, List<TrainingSample> test) {
        int cut = (int) Math.round(src.size() * 0.8);
        train.addAll(src.subList(0, cut));
        test.addAll(src.subList(cut, src.size()));
    }

    public static Map<String, Double> evaluate(LogisticModel m, List<TrainingSample> test, double threshold, int trainSize) {
        int tp = 0, fp = 0, tn = 0, fn = 0;
        List<double[]> scored = new ArrayList<>();
        for (TrainingSample s : test) {
            double p = m.probability(s.x());
            scored.add(new double[]{p, s.label()});
            boolean flagged = p >= threshold;
            if (flagged && s.label() == 1) tp++;
            else if (flagged) fp++;
            else if (s.label() == 1) fn++;
            else tn++;
        }
        double precision = tp + fp == 0 ? 0 : (double) tp / (tp + fp);
        double recall = tp + fn == 0 ? 0 : (double) tp / (tp + fn);
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("auc", auc(scored));
        metrics.put("precision", precision);
        metrics.put("recall", recall);
        metrics.put("f1", precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall));
        metrics.put("falsePositiveRate", fp + tn == 0 ? 0 : (double) fp / (fp + tn));
        metrics.put("tp", (double) tp);
        metrics.put("fp", (double) fp);
        metrics.put("tn", (double) tn);
        metrics.put("fn", (double) fn);
        metrics.put("trainSize", (double) trainSize);
        metrics.put("testSize", (double) test.size());
        return metrics;
    }

    /** ROC AUC via the Mann-Whitney U statistic, with ties counted as half. */
    static double auc(List<double[]> scored) {
        scored.sort(Comparator.comparingDouble(a -> a[0]));
        long pos = scored.stream().filter(a -> a[1] == 1).count();
        long neg = scored.size() - pos;
        if (pos == 0 || neg == 0) return 0.5;
        double rankSum = 0;
        int i = 0;
        while (i < scored.size()) {
            int j = i;
            while (j + 1 < scored.size() && scored.get(j + 1)[0] == scored.get(i)[0]) j++;
            double avgRank = (i + j) / 2.0 + 1;
            for (int k = i; k <= j; k++) {
                if (scored.get(k)[1] == 1) rankSum += avgRank;
            }
            i = j + 1;
        }
        return (rankSum - pos * (pos + 1) / 2.0) / (pos * neg);
    }
}
