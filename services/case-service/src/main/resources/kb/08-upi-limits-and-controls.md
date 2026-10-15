---
title: UPI Limits and Platform Controls
source: Summary of NPCI UPI limits and common bank and PSP controls, as described by banks and payment providers (2025). Banks may set lower limits.
---

## Transaction limits
The standard NPCI limit for most person-to-person and person-to-merchant UPI payments is ₹1 lakh per transaction. Higher limits apply to specific merchant categories, such as capital markets, insurance, loan repayment, credit card bill payments, tax payments, hospitals and educational institutions, up to a few lakh rupees. Individual banks and apps may set lower limits and daily transaction-count caps (commonly around 20 transactions in 24 hours).

## Cooling period for new users and devices
After a new UPI registration, a device change, a SIM change or a UPI PIN reset, banks and PSP apps typically apply a reduced limit (commonly ₹5,000) for an initial period, often the first 24 hours. This control directly targets account takeover: the first payments from a new device are the riskiest. FraudShield's NEW_DEVICE and PAYER_NEW_ACCOUNT signals reflect the same idea.

## Real-time controls a bank can apply
- Step-up authentication: hold the payment and ask the customer to re-confirm through a trusted channel (FraudShield REVIEW).
- Decline (FraudShield BLOCK).
- Beneficiary cooling: delay first payments to a new payee above a threshold.
- Deny-lists of confirmed fraudulent VPAs, shared across the institution in real time.
