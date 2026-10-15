package com.fraudshield.risk.feature;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Dense feature vector indexed by {@link Feature#ordinal()}. */
public record FeatureVector(double[] values) {

    public static final int SIZE = Feature.values().length;

    public FeatureVector {
        if (values.length != SIZE) {
            throw new IllegalArgumentException("Expected " + SIZE + " features but got " + values.length);
        }
    }

    public double get(Feature f) {
        return values[f.ordinal()];
    }

    public Map<String, Double> asMap() {
        Map<String, Double> map = new LinkedHashMap<>();
        for (Feature f : Feature.values()) {
            map.put(f.name(), values[f.ordinal()]);
        }
        return map;
    }

    public String toCsv() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(values[i]);
        }
        return sb.toString();
    }

    public static FeatureVector fromCsv(String csv) {
        return new FeatureVector(Arrays.stream(csv.split(",")).mapToDouble(Double::parseDouble).toArray());
    }
}
