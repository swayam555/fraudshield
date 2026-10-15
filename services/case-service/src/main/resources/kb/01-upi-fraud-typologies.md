---
title: UPI Fraud Typologies Field Guide
source: FraudShield internal training material, compiled from public reporting by RBI, I4C/MHA and news coverage (2024-2026)
---

## Money mule accounts
A money mule account receives the proceeds of fraud and moves them on, so the real beneficiary stays hidden. Mule accounts are often opened with real KYC by people recruited through "work from home", "task reward" or "part-time job" offers, or are rented or bought outright. The CBI's Operation Chakra-V reportedly identified more than 8.5 lakh suspected mule accounts across 700+ bank branches. RBI's Innovation Hub built MuleHunter.AI to help banks detect them.

Typical indicators:
- Fan-in: many unrelated people pay the same account within a short window, often with notes such as "task deposit" or "reward".
- Pass-through or layering: most of the money received is sent onward within minutes, usually to one collector account or split across several.
- The account is new or was dormant until recently, and its balance stays close to zero between bursts.
- Several mule accounts operate from the same device, IP range or SIM ("device farms").
- Inbound payments from victims are themselves often flagged or reported by the victims' banks.

## Account takeover (ATO) and SIM swap
In an account takeover, a fraudster gains control of the victim's UPI app, usually through a SIM swap, a remote-access app ("screen sharing" support scams), malware or phished credentials. Once in, they drain the account to new payees as fast as possible.

Typical indicators:
- First use of a new device for the account, often right after a UPI PIN reset or device re-binding.
- A location far from the customer's normal pattern, sometimes physically impossible given the previous payment's time and place (impossible travel).
- Several high-value payments to payees the customer has never paid before, in quick succession.
- Amounts far above the customer's normal spending.

## Social-engineering scams, including "digital arrest"
In an authorised push payment scam, the genuine customer makes the payment on their own device, but under deception or coercion. "Digital arrest" scams impersonate police, CBI, customs, the RBI or the cyber cell on video calls. They tell the victim they are implicated in a crime and must transfer money for "verification", a "security deposit" or "case clearance". Senior citizens are disproportionately targeted. Other variants include fake investment and trading groups, fake customer-care numbers, and electricity-disconnection threats.

Typical indicators:
- The customer's own known device and normal location (so device and location checks pass).
- First payment to a new payee, at an amount far above the customer's history.
- Escalating amounts in a short period, often close to the per-transaction limit.
- Notes such as "deposit", "verification", "clearance", "refundable" or "fine".
- No government agency ever asks for money over UPI or for a "digital arrest". This is a decisive red flag.

## UPI ID probing and enumeration
Fraudsters test which UPI IDs (VPAs) are live and active by sending many tiny payments (₹1 or similar) or collect requests to lists of guessed IDs. Live accounts are then targeted with collect-request or QR scams.

Typical indicators:
- A new account sending a burst of ₹1 to ₹10 payments to many unrelated payees within minutes.
- A high fan-out (distinct payees) with negligible total value.
- A new device, often shared with other suspicious accounts.

## Collect-request and QR code scams
In these scams the victim is tricked into approving a collect request or scanning a QR code "to receive money". In UPI, entering a PIN always means paying, never receiving. Sellers on classifieds sites are frequent targets.
