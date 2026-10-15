package com.fraudshield.risk.scoring;

import com.fraudshield.contracts.ScoreRequest;
import com.fraudshield.risk.feature.Feature;
import com.fraudshield.risk.feature.RawSignals;

import java.text.NumberFormat;
import java.util.Locale;

/** Turns a feature into a sentence an analyst (or a customer support agent) can read out. */
final class ReasonFormatter {

    private static final NumberFormat INR = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));

    private ReasonFormatter() {
    }

    static String describe(Feature f, RawSignals s, ScoreRequest r) {
        return switch (f) {
            case PAYER_VELOCITY_1M -> s.payerTxnLastMinute() + " payments from this account in the last minute";
            case PAYER_VELOCITY_1H -> s.payerTxnLastHour() + " payments from this account in the last hour";
            case AMOUNT_ANOMALY -> INR.format(r.amount()) + " vs a usual " + INR.format(s.payerMeanAmount())
                    + " across " + s.payerHistoryCount() + " past payments";
            case AMOUNT_SIZE -> "Large payment of " + INR.format(r.amount());
            case NEW_DEVICE -> "Device " + r.deviceId() + " has never been used by this account ("
                    + s.knownDeviceCount() + " known devices)";
            case NEW_PAYEE -> "First ever payment from " + r.payerVpa() + " to " + r.payeeVpa();
            case IMPOSSIBLE_TRAVEL -> "Location jumped further than a flight could cover since the previous payment";
            case PAYEE_FAN_IN -> s.payeeDistinctPayers() + " different accounts paid " + r.payeeVpa()
                    + " in the last hour (classic mule collection pattern)";
            case PAYER_FAN_OUT -> "Account paid " + s.payerDistinctPayees() + " different payees in the last hour";
            case PASS_THROUGH -> INR.format(s.payerInflow30m()) + " arrived in the last 30 minutes and is being sent"
                    + " straight on (layering)";
            case SHARED_DEVICE -> "Device " + r.deviceId() + " is used by " + s.accountsOnDevice() + " accounts";
            case MICRO_PROBING -> s.microPayments10m() + " payments of ₹10 or less in 10 minutes (account testing)";
            case ODD_HOUR -> "Made between 1 AM and 5 AM IST";
            case PAYEE_WATCHLIST -> r.payeeVpa() + " is on the mule watch-list (score "
                    + String.format("%.2f", s.payeeMuleScore()) + ")";
            case PAYEE_NEW_ACCOUNT -> r.payeeVpa() + " was first seen less than 24 hours ago";
            case PAYER_NEW_ACCOUNT -> r.payerVpa() + " was first seen less than 24 hours ago";
            case PAYER_TRUST -> "Established payer with " + s.payerHistoryCount() + " past payments";
        };
    }
}
