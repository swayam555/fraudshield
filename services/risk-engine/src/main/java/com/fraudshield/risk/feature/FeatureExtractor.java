package com.fraudshield.risk.feature;

import java.time.Instant;
import java.time.ZoneId;

import static com.fraudshield.risk.feature.Feature.*;

/** Turns raw Redis counters into scaled model features. Pure function, so it is unit-tested directly. */
public final class FeatureExtractor {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final long DAY_MS = 86_400_000L;

    private FeatureExtractor() {
    }

    public static FeatureVector extract(RawSignals s, double amount, Double lat, Double lon, Instant at) {
        double[] v = new double[FeatureVector.SIZE];
        long now = at.toEpochMilli();

        // One earlier payment in the last minute is normal (a retry, a split bill); bursts are not.
        v[PAYER_VELOCITY_1M.ordinal()] = clip(Math.max(0, s.payerTxnLastMinute() - 1) / 4.0, 3);
        v[PAYER_VELOCITY_1H.ordinal()] = clip(Math.max(0, s.payerTxnLastHour() - 5) / 15.0, 3);

        double sd = s.payerAmountStdDev();
        if (s.payerHistoryCount() >= 5) {
            // A floor on the deviation stops a payer who always pays exactly ₹100 from
            // looking anomalous when they pay ₹120.
            double floor = Math.max(sd, Math.max(50, 0.25 * s.payerMeanAmount()));
            double z = (amount - s.payerMeanAmount()) / floor;
            v[AMOUNT_ANOMALY.ordinal()] = clip(Math.max(0, z) / 3.0, 3);
        }
        v[AMOUNT_SIZE.ordinal()] = clip(Math.log10(Math.max(1, amount)) / 5.0, 1.5);

        // A device counts as new for 24 hours after it is first bound, as long as the account
        // already had another device: the window in which account takeovers drain accounts.
        boolean brandNewDevice = !s.deviceKnown() && s.knownDeviceCount() > 0;
        boolean recentDevice = s.deviceKnown() && s.knownDeviceCount() > 1
                && now - s.deviceFirstSeenMillis() < DAY_MS;
        v[NEW_DEVICE.ordinal()] = (brandNewDevice || recentDevice) ? 1 : 0;
        v[NEW_PAYEE.ordinal()] = (!s.payeeKnown() || now - s.payeeFirstPaidMillis() < DAY_MS) ? 1 : 0;

        if (lat != null && lon != null && s.lastLat() != null && s.lastLon() != null && s.lastTsMillis() != null) {
            double km = haversineKm(s.lastLat(), s.lastLon(), lat, lon);
            double hours = Math.max((now - s.lastTsMillis()) / 3_600_000.0, 1.0 / 60);
            double kmph = km / hours;
            // Anything a flight could explain (< 900 km/h) scores zero.
            v[IMPOSSIBLE_TRAVEL.ordinal()] = km < 50 ? 0 : clip(Math.max(0, kmph - 900) / 1000.0, 3);
        }

        boolean payeeYoung = s.payeeFirstSeenMillis() == null || now - s.payeeFirstSeenMillis() < DAY_MS;
        // Fan-in is the signature of a busy merchant as much as of a mule. It only carries full
        // weight when the payee also looks like a conduit: a new account, or one moving money out.
        double conduit = (payeeYoung || s.payeeOutflow1h() > 0) ? 1.0 : 0.15;
        v[PAYEE_FAN_IN.ordinal()] = clip(Math.max(0, s.payeeDistinctPayers() - 2) / 8.0, 3) * conduit;
        v[PAYER_FAN_OUT.ordinal()] = clip(Math.max(0, s.payerDistinctPayees() - 2) / 5.0, 3);

        double available = s.payerInflow30m() - s.payerOutflow30m();
        if (s.payerInflow30m() > 0 && available > 0) {
            // Share of the money received in the last 30 minutes that this payment sends straight
            // on. Spending ₹50 of a ₹5,000 transfer is life; forwarding ₹4,600 of it is layering.
            double share = Math.min(amount, available) / s.payerInflow30m();
            v[PASS_THROUGH.ordinal()] = clip(share * (s.payerInflow30m() >= 1000 ? 1 : 0.3), 1);
        }

        v[SHARED_DEVICE.ordinal()] = clip(Math.max(0, s.accountsOnDevice() - 2) / 3.0, 3);
        v[MICRO_PROBING.ordinal()] = clip(s.microPayments10m() / 3.0, 3);

        int hour = at.atZone(IST).getHour();
        v[ODD_HOUR.ordinal()] = (hour >= 1 && hour < 5) ? 1 : 0;

        v[PAYEE_WATCHLIST.ordinal()] = clip(s.payeeMuleScore(), 1);
        v[PAYEE_NEW_ACCOUNT.ordinal()] = payeeYoung ? 1 : 0;
        v[PAYER_NEW_ACCOUNT.ordinal()] =
                (s.payerFirstSeenMillis() == null || now - s.payerFirstSeenMillis() < DAY_MS) ? 1 : 0;
        v[PAYER_TRUST.ordinal()] = clip(Math.log1p(s.payerHistoryCount()) / 5.0, 1.2);

        return new FeatureVector(v);
    }

    static double clip(double value, double max) {
        return Math.max(0, Math.min(max, value));
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
