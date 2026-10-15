package com.fraudshield.txn.sim;

import com.fraudshield.txn.payment.PaymentRequest;

import java.time.Instant;

record SimulatedPayment(PaymentRequest request, Scenario label, Instant at) {

    SimulatedPayment withTime(Instant time) {
        return new SimulatedPayment(request, label, time);
    }
}
