package com.fraudshield.risk.feature;

/**
 * Model features, each scaled to roughly [0, 3] so that a single learning rate trains well
 * and weights are comparable when explaining a decision.
 */
public enum Feature {
    PAYER_VELOCITY_1M("High payment velocity (last minute)"),
    PAYER_VELOCITY_1H("High payment velocity (last hour)"),
    AMOUNT_ANOMALY("Amount far above this payer's usual spend"),
    AMOUNT_SIZE("Large absolute amount"),
    NEW_DEVICE("Payment from a device never seen for this account"),
    NEW_PAYEE("First payment to this payee"),
    IMPOSSIBLE_TRAVEL("Location jump faster than physically possible"),
    PAYEE_FAN_IN("Many different people paying the same account"),
    PAYER_FAN_OUT("Payer sending to many different accounts"),
    PASS_THROUGH("Funds forwarded soon after they were received"),
    SHARED_DEVICE("Device shared by many accounts"),
    MICRO_PROBING("Burst of tiny 'test' payments"),
    ODD_HOUR("Payment made between 1 AM and 5 AM IST"),
    PAYEE_WATCHLIST("Payee is on the mule watch-list"),
    PAYEE_NEW_ACCOUNT("Payee account first seen in the last 24 hours"),
    PAYER_NEW_ACCOUNT("Payer account first seen in the last 24 hours"),
    PAYER_TRUST("Long, consistent payment history");

    private final String description;

    Feature(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
