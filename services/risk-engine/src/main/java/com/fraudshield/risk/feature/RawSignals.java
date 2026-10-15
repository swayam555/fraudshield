package com.fraudshield.risk.feature;

/** Raw counters as read from Redis, before any normalisation. */
public record RawSignals(
        long payerTxnLastMinute,
        long payerTxnLastHour,
        long payerHistoryCount,
        double payerMeanAmount,
        double payerAmountM2,
        Double lastLat,
        Double lastLon,
        Long lastTsMillis,
        Long deviceFirstSeenMillis,
        long knownDeviceCount,
        Long payeeFirstPaidMillis,
        long payeeDistinctPayers,
        long payerDistinctPayees,
        double payerInflow30m,
        double payerOutflow30m,
        long accountsOnDevice,
        long microPayments10m,
        Long payeeFirstSeenMillis,
        Long payerFirstSeenMillis,
        boolean payeeDenied,
        double payeeMuleScore,
        double payeeOutflow1h) {

    public boolean deviceKnown() {
        return deviceFirstSeenMillis != null;
    }

    public boolean payeeKnown() {
        return payeeFirstPaidMillis != null;
    }

    public double payerAmountStdDev() {
        return payerHistoryCount < 2 ? 0 : Math.sqrt(payerAmountM2 / (payerHistoryCount - 1));
    }
}
