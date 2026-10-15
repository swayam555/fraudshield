package com.fraudshield.risk.model;

import com.fraudshield.risk.feature.FeatureVector;

public record TrainingSample(FeatureVector x, int label) {

    public String encode() {
        return label + "|" + x.toCsv();
    }

    public static TrainingSample decode(String s) {
        int bar = s.indexOf('|');
        return new TrainingSample(FeatureVector.fromCsv(s.substring(bar + 1)), Integer.parseInt(s.substring(0, bar)));
    }
}
