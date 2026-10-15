package com.fraudshield.risk.model;

import com.fraudshield.risk.feature.Feature;
import com.fraudshield.risk.feature.FeatureVector;

import java.time.Instant;
import java.util.Map;

/**
 * Logistic-regression risk model. A linear model is a deliberate choice: each feature's
 * contribution to a decision is exactly {@code weight * value}, so every block can be explained
 * to the customer, the analyst and the regulator without a separate explainer.
 */
public record LogisticModel(
        String version,
        double bias,
        double[] weights,
        Instant trainedAt,
        Map<String, Double> metrics) {

    public double logit(FeatureVector x) {
        double z = bias;
        for (int i = 0; i < weights.length; i++) {
            z += weights[i] * x.values()[i];
        }
        return z;
    }

    public double probability(FeatureVector x) {
        return sigmoid(logit(x));
    }

    public double contribution(Feature f, FeatureVector x) {
        return weights[f.ordinal()] * x.get(f);
    }

    public static double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
    }

    /** Expert-set starting weights, used until enough labelled traffic exists to train. */
    public static LogisticModel baseline() {
        double[] w = new double[FeatureVector.SIZE];
        w[Feature.PAYER_VELOCITY_1M.ordinal()] = 1.5;
        w[Feature.PAYER_VELOCITY_1H.ordinal()] = 0.8;
        w[Feature.AMOUNT_ANOMALY.ordinal()] = 1.3;
        w[Feature.AMOUNT_SIZE.ordinal()] = 1.0;
        w[Feature.NEW_DEVICE.ordinal()] = 1.4;
        w[Feature.NEW_PAYEE.ordinal()] = 0.8;
        w[Feature.IMPOSSIBLE_TRAVEL.ordinal()] = 1.8;
        w[Feature.PAYEE_FAN_IN.ordinal()] = 2.2;
        w[Feature.PAYER_FAN_OUT.ordinal()] = 1.2;
        w[Feature.PASS_THROUGH.ordinal()] = 2.4;
        w[Feature.SHARED_DEVICE.ordinal()] = 1.5;
        w[Feature.MICRO_PROBING.ordinal()] = 2.0;
        w[Feature.ODD_HOUR.ordinal()] = 0.9;
        w[Feature.PAYEE_WATCHLIST.ordinal()] = 3.5;
        w[Feature.PAYEE_NEW_ACCOUNT.ordinal()] = 0.6;
        w[Feature.PAYER_NEW_ACCOUNT.ordinal()] = 0.6;
        w[Feature.PAYER_TRUST.ordinal()] = -1.0;
        return new LogisticModel("baseline-v0", -5.2, w, Instant.EPOCH, Map.of());
    }
}
