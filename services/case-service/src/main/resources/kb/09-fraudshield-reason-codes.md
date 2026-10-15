---
title: FraudShield Reason Codes - What They Mean and How to Verify
source: FraudShield model documentation
---

## How scoring works
Every payment is scored in real time by a logistic-regression model over 17 behavioural features held in a Redis feature store. Each feature's contribution to the log-odds of fraud is its weight multiplied by its value, so every decision lists the exact signals that drove it. A probability of 0.50 or more is held for review (step-up authentication), and 0.85 or more is blocked. Confirmed fraud (analyst verdicts) and synthetic ground truth feed a retraining loop. A new model is promoted only if it scores at least as well as the current one on the hold-out set.

## Velocity and amount signals
- PAYER_VELOCITY_1M / PAYER_VELOCITY_1H: unusually many payments from the account in the last minute or hour. Verify whether a bot or a takeover is draining the account.
- AMOUNT_ANOMALY: the amount is many standard deviations above this payer's usual spend (Welford running statistics). This is common in scams and takeovers. Verify by checking the customer's history for one-off big purchases.
- AMOUNT_SIZE: a large absolute amount. On its own this is weak; it matters in combination with other signals.

## Device, payee and location signals
- NEW_DEVICE: the device has never been used by this account, although the account has known devices. This is the core ATO signal.
- NEW_PAYEE: the first payment from this payer to this payee.
- IMPOSSIBLE_TRAVEL: the implied speed between consecutive payment locations exceeds what a flight could cover.
- SHARED_DEVICE: the device is used by more than two accounts. This suggests a device farm running mule accounts.

## Network signals
- PAYEE_FAN_IN: many distinct payers into the payee within roughly the last hour (HyperLogLog distinct count). This is the classic mule collection signal. Check for merchant false positives.
- PAYER_FAN_OUT: the payer is paying many distinct payees. Seen in probing and in the dispersal stage of layering.
- PASS_THROUGH: the payment is funded by money received in the last 30 minutes that has not been sent on yet. This is the account behaving as a conduit.
- PAYEE_WATCHLIST: the payee has a high mule score from the asynchronous graph analyser.
- PAYEE_DENYLISTED: the payee was confirmed as fraudulent by an analyst. These payments are always blocked.

## Account age and context
- PAYEE_NEW_ACCOUNT / PAYER_NEW_ACCOUNT: the account was first seen less than 24 hours ago.
- MICRO_PROBING: a burst of payments of ₹10 or less in 10 minutes.
- ODD_HOUR: the payment was made between 1 AM and 5 AM IST.
- PAYER_TRUST: a long, consistent payment history. This is a negative weight that lowers risk.
