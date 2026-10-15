package com.fraudshield.risk;

import com.fraudshield.risk.feature.Feature;
import com.fraudshield.risk.feature.FeatureExtractor;
import com.fraudshield.risk.feature.FeatureVector;
import com.fraudshield.risk.feature.RawSignals;
import com.fraudshield.risk.model.LogisticModel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureExtractorTest {

    private static final Instant NOON_IST = ZonedDateTime.of(2026, 3, 10, 12, 0, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();
    private static final long OLD = NOON_IST.toEpochMilli() - 90L * 86_400_000L;

    private static RawSignals regularPayer() {
        // 60 past payments averaging ₹400, known device, known payee, Bengaluru.
        return new RawSignals(0, 1, 60, 400, 59 * 150.0 * 150.0, 12.97, 77.59,
                NOON_IST.toEpochMilli() - 3_600_000L, OLD, 1, OLD, 1, 1, 0, 0, 1, 0, OLD, OLD, false, 0, 0);
    }

    @Test
    void everydayPaymentIsLowRisk() {
        FeatureVector x = FeatureExtractor.extract(regularPayer(), 350, 12.97, 77.59, NOON_IST);
        assertThat(LogisticModel.baseline().probability(x)).isLessThan(0.05);
        assertThat(x.get(Feature.NEW_DEVICE)).isZero();
        assertThat(x.get(Feature.IMPOSSIBLE_TRAVEL)).isZero();
    }

    @Test
    void accountTakeoverIsBlocked() {
        RawSignals s = new RawSignals(0, 1, 60, 400, 59 * 150.0 * 150.0, 12.97, 77.59,
                NOON_IST.toEpochMilli() - 1_800_000L, null, 1, null, 0, 1, 0, 0, 1, 0, null, OLD, false, 0, 0);
        // New device, new payee, ₹49,000 and 1,700 km from the last payment 30 minutes ago.
        FeatureVector x = FeatureExtractor.extract(s, 49_000, 28.61, 77.20, NOON_IST);
        assertThat(x.get(Feature.NEW_DEVICE)).isEqualTo(1);
        assertThat(x.get(Feature.IMPOSSIBLE_TRAVEL)).isGreaterThan(1);
        assertThat(LogisticModel.baseline().probability(x)).isGreaterThan(0.85);
    }

    @Test
    void forwardingFreshlyReceivedMoneyIsPassThrough() {
        RawSignals s = new RawSignals(0, 2, 2, 100, 0, null, null, null, OLD, 1, null, 0, 1,
                48_000, 0, 1, 0, null, NOON_IST.toEpochMilli() - 3_600_000L, false, 0, 0);
        FeatureVector x = FeatureExtractor.extract(s, 45_000, null, null, NOON_IST);
        // ₹45,000 of the ₹48,000 just received is being sent on.
        assertThat(x.get(Feature.PASS_THROUGH)).isCloseTo(0.9375, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(x.get(Feature.PAYER_NEW_ACCOUNT)).isEqualTo(1.0);
    }

    @Test
    void spendingASmallPartOfAnIncomingTransferIsNotPassThrough() {
        RawSignals s = new RawSignals(0, 2, 40, 300, 0, null, null, null, OLD, 1, OLD, 0, 1,
                5_000, 0, 1, 0, OLD, OLD, false, 0, 0);
        assertThat(FeatureExtractor.extract(s, 50, null, null, NOON_IST).get(Feature.PASS_THROUGH)).isLessThan(0.02);
    }

    @Test
    void busyEstablishedMerchantIsNotTreatedAsMule() {
        // 40 distinct payers in the hour, but the merchant is old and never forwards money.
        RawSignals merchant = new RawSignals(0, 1, 60, 400, 59 * 150.0 * 150.0, null, null, null, OLD, 1, OLD,
                40, 1, 0, 0, 1, 0, OLD, OLD, false, 0, 0);
        RawSignals mule = new RawSignals(0, 1, 60, 400, 59 * 150.0 * 150.0, null, null, null, OLD, 1, OLD,
                40, 1, 0, 0, 1, 0, null, OLD, false, 0, 0);
        double merchantFanIn = FeatureExtractor.extract(merchant, 300, null, null, NOON_IST).get(Feature.PAYEE_FAN_IN);
        double muleFanIn = FeatureExtractor.extract(mule, 300, null, null, NOON_IST).get(Feature.PAYEE_FAN_IN);
        assertThat(merchantFanIn).isLessThan(0.5);
        assertThat(muleFanIn).isEqualTo(3.0);
    }

    @Test
    void deviceStaysNewForADayAfterBinding() {
        long tenMinutesAgo = NOON_IST.toEpochMilli() - 600_000L;
        RawSignals s = new RawSignals(0, 1, 60, 400, 59 * 150.0 * 150.0, null, null, null, tenMinutesAgo, 2, OLD,
                1, 1, 0, 0, 1, 0, OLD, OLD, false, 0, 0);
        assertThat(FeatureExtractor.extract(s, 300, null, null, NOON_IST).get(Feature.NEW_DEVICE)).isEqualTo(1);
    }

    @Test
    void lateNightIsFlaggedInIndianTime() {
        Instant threeAm = ZonedDateTime.of(2026, 3, 10, 3, 0, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();
        assertThat(FeatureExtractor.extract(regularPayer(), 300, null, null, threeAm).get(Feature.ODD_HOUR)).isEqualTo(1);
        assertThat(FeatureExtractor.extract(regularPayer(), 300, null, null, NOON_IST).get(Feature.ODD_HOUR)).isZero();
    }

    @Test
    void haversineMatchesKnownDistance() {
        // Mumbai to Delhi is roughly 1,150 km as the crow flies.
        assertThat(FeatureExtractor.haversineKm(19.076, 72.8777, 28.6139, 77.209)).isBetween(1100.0, 1200.0);
    }
}
