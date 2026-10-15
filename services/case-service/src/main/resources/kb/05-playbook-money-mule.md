---
title: Investigation Playbook - Money Mule Accounts
source: FraudShield internal playbook
---

## Triage
1. Open the payment graph for the account. Confirm the fan-in (how many distinct payers in the last hour or day) and the onward transfers (to whom, how fast, and what share of inflows).
2. Check the account's age, KYC details and declared occupation. Mule accounts are often new, or dormant accounts that were recently reactivated.
3. Check device linkage. If the device is shared with other accounts, those accounts are probably part of the same network.
4. Check whether any inbound payments have been reported by the payers' banks or through the national cybercrime reporting system.

## Containment
- Apply a debit freeze or lien on the account balance, following the bank's policy and any instructions received from law enforcement.
- Add the account to the deny-list so that new inbound payments from other victims are blocked in real time. In FraudShield, resolving the case as FRAUD does this automatically.
- Trace the onward recipients (the collector accounts) and open linked cases for them.

## Evidence and reporting
- Record the timeline: first inbound victim payment, onward transfers and the amounts involved.
- Escalate to the Principal Officer for an STR to FIU-IND.
- Before any formal fraud classification of the account holder, follow the natural-justice process: issue a show cause notice and allow at least 21 days to respond.

## False-positive checks
High fan-in alone is normal for merchants, small shops and collection accounts for societies or events. Look for the combination of fan-in with rapid pass-through and a new account before concluding the account is a mule. A shop that receives many payments but does not forward them is not a mule.
