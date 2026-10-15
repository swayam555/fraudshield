package com.fraudshield.contracts;

public enum Decision {
    /** Payment goes through. */
    APPROVE,
    /** Payment is held and the payer must re-authenticate (step-up). An analyst case is opened. */
    REVIEW,
    /** Payment is declined outright. */
    BLOCK
}
