package com.fraudshield.risk;

import com.fraudshield.risk.feature.Feature;
import com.fraudshield.risk.feature.FeatureVector;
import com.fraudshield.risk.model.LogisticModel;
import com.fraudshield.risk.model.Trainer;
import com.fraudshield.risk.model.TrainingSample;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrainerTest {

    @Test
    void learnsASignalTheBaselineIgnores() {
        // Fraud in this synthetic set is driven by SHARED_DEVICE alone; the baseline weight is
        // modest, so training should raise it and reach a near-perfect AUC.
        Random rnd = new Random(7);
        List<TrainingSample> samples = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            boolean fraud = i % 10 == 0;
            double[] v = new double[FeatureVector.SIZE];
            v[Feature.AMOUNT_SIZE.ordinal()] = 0.4 + rnd.nextDouble() * 0.4;
            v[Feature.PAYER_TRUST.ordinal()] = rnd.nextDouble();
            v[Feature.SHARED_DEVICE.ordinal()] = fraud ? 1 + rnd.nextDouble() : rnd.nextDouble() * 0.2;
            samples.add(new TrainingSample(new FeatureVector(v), fraud ? 1 : 0));
        }
        LogisticModel baseline = LogisticModel.baseline();
        Trainer.Result result = Trainer.defaults().train(samples, baseline, "test", 0.5);

        assertThat(result.model().metrics().get("auc")).isGreaterThan(0.98);
        assertThat(result.model().metrics().get("recall")).isGreaterThan(0.9);
        assertThat(result.model().weights()[Feature.SHARED_DEVICE.ordinal()])
                .isGreaterThan(baseline.weights()[Feature.SHARED_DEVICE.ordinal()]);
    }

    @Test
    void refusesToTrainWithoutBothClasses() {
        List<TrainingSample> onlyLegit = new ArrayList<>();
        for (int i = 0; i < 50; i++) onlyLegit.add(new TrainingSample(new FeatureVector(new double[FeatureVector.SIZE]), 0));
        assertThatThrownBy(() -> Trainer.defaults().train(onlyLegit, LogisticModel.baseline(), "x", 0.5))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void encodesAndDecodesSamples() {
        double[] v = new double[FeatureVector.SIZE];
        v[3] = 0.25;
        TrainingSample s = new TrainingSample(new FeatureVector(v), 1);
        TrainingSample back = TrainingSample.decode(s.encode());
        assertThat(back.label()).isEqualTo(1);
        assertThat(back.x().values()).containsExactly(v);
    }
}
