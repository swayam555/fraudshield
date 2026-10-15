---
title: Investigation Playbook - Account Takeover and SIM Swap
source: FraudShield internal playbook
---

## Triage
1. Compare the device and location of the flagged payments with the customer's history. A new device plus a distant or impossible location is the strongest ATO signal.
2. Check for a recent UPI PIN reset, a device re-registration or a SIM change on the registered mobile number.
3. List every payment made from the new device. All of them are suspect, not just the one that was flagged.

## Containment
- De-register the new device from the customer's UPI profile and block further payments from it.
- Contact the customer only on their registered contact details, not on a number supplied in the session.
- Ask the customer to reset their UPI PIN and banking credentials from a trusted device.
- Request holds on the beneficiary accounts through the national cybercrime reporting channel, and file a complaint on the customer's behalf or help them to do so.

## Liability
An ATO is generally an unauthorised transaction. Record when the customer was alerted and when they responded, because the customer-liability rules depend on the reporting delay (zero liability for a third-party breach reported within three working days). Make the shadow reversal within 10 working days of the customer's report, where applicable.
