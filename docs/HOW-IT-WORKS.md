# How FraudShield works

Part 1 is for everyone: designers, product people, recruiters. Part 2 is for engineers. The
[README](../README.md) has the architecture diagram and the table of engineering decisions; this page
walks through what actually happens.

---

## Part 1: in plain English

### Three stories

**Divya pays her gas bill.** She lives in Chennai, uses the same phone every day, and usually pays
around ₹356. Her ₹356 payment to the gas company is scored in a few milliseconds: familiar phone,
familiar city, familiar payee, normal amount. **Approved.** She never knows FraudShield exists. That's
what happens to about 99.7% of genuine payments.

**Divya's phone is stolen.** Ten minutes later, someone in Delhi tries to send ₹74,000 from her account
to a payee she has never paid, from a phone never seen on her account. FraudShield sees a new device,
a location 1,750 km away (too far to have travelled), an amount 200× her usual, and a brand-new payee.
**Blocked**, with those reasons in plain English. A case opens for an investigator.

**A mule ring.** Twenty victims of a job scam each pay "registration fees" into a handful of new
accounts. Each payment looks small and harmless on its own. But FraudShield also watches the network:
these accounts are a day old, receive money from many strangers, and pass it on within minutes. Their
mule score rises with every payment, they go on a watch-list, and the next payment *into* them is held.
One case groups everything about each mule account.

### What the investigator does

1. Opens the **case queue**, sorted by priority, with a timer for how long each case has been waiting.
2. Opens a case and sees the flagged payments, a **map of where the money went**, and the evidence.
3. Asks the **AI copilot** for a brief. It writes a summary, the likely scam type, recommended actions
   and whether a Suspicious Transaction Report is needed, citing RBI and FIU-IND guidance.
4. Marks the case **Fraud** or **Genuine**. A fraud verdict immediately deny-lists the accounts
   involved, and the verdict becomes training data that makes the model better.

### What the risk team does

- Watches the **live monitor**: payments per second, decision time, money protected.
- Launches **simulated attacks** to check the system catches them.
- On the **Risk model** page, sees how accurate the model is, retrains it with one click, and moves the
  approve / review / block thresholds live during a scam wave.

---

## Part 2: for engineers

### Services

| Service | Port | Owns |
|---|---|---|
| `gateway` | 8080 | JWT verification, per-user Redis rate limits (tight on `/api/copilot`) |
| `txn-service` | 8081 | payment intake, idempotency, the risk-engine client (300 ms budget, circuit breaker), payments DB, outbox, traffic simulator |
| `risk-engine` | 8082 | Redis feature store (Lua), logistic-regression model and trainer, thresholds, mule graph analyser, feedback consumer |
| `case-service` | 8083 | analysts and sign-in, cases, live SSE feed, knowledge base (pgvector), copilot |

### 1. A payment is scored (synchronous path)

1. `POST /api/payments` reaches `txn-service` through the gateway. Validation: real UPI ID formats,
   ₹1 to ₹5,00,000 (the UPI per-transaction limit).
2. If an `Idempotency-Key` header is present, `SETNX` in Redis claims it. A retry gets the original
   answer back (a unique constraint in Postgres backs this up).
3. `RiskClient` calls `risk-engine` with a **300 ms** timeout inside a Resilience4j circuit breaker.
4. `FeatureStore` runs **one Lua script** that reads every signal for this payer, payee and device *and*
   records the attempt, atomically and in one round trip: sorted sets for velocity windows,
   HyperLogLog for distinct counterparties, money-flow windows, micro-payment counts and shared devices.
   The payer's *trusted* profile (Welford's online mean and variance for "usual amount", first-seen
   hashes for devices and payees, and the last location for impossible travel) is only read here. It
   is updated by a second script, `learn.lua`, after the decision and only if the payment was
   approved, so an attacker's blocked retries never make their phone, city or amount look normal.
5. `FeatureExtractor` turns those raw signals into 17 scaled features (velocity, amount anomaly, new
   device, new payee, impossible travel, fan-in, fan-out, pass-through, shared device, micro-probing,
   odd hour, watch-list, new payee account, new payer account, long trusted history, and so on).
6. `LogisticModel` produces a probability. Each feature's contribution is exactly *weight × value*,
   so the top contributions *are* the explanation: no separate explainer model.
7. Thresholds (defaults: **review ≥ 0.5, block ≥ 0.85**, adjustable live) give `APPROVE`, `REVIEW`
   or `BLOCK`.
8. `txn-service` stores the payment and an outbox row in one transaction, and returns the decision
   with its reasons.

**If the risk engine is slow or down**, the breaker opens and a fallback decides: payments up to
₹2,000 are approved (fail open), larger ones are held (fail closed). The decision is marked
`degraded`, and `DegradedBackfill` replays those payments into the feature store from Kafka when the
engine recovers, so customers' behavioural history doesn't get holes in it.

### 2. Asynchronous analysis (Kafka)

```
txn-service ──payments.scored──▶ risk-engine (MuleAnalyzer)  ──risk.alerts──▶ case-service
            ──payments.scored──▶ case-service (cases, live feed)
case-service ──case.labels────▶ risk-engine (deny-list, training labels)
```

- **MuleAnalyzer** keeps a payment graph in Redis and a mule score per account. It's built from
  fan-in, pass-through (money out soon after money in), forwarding and shared devices, and fan-in only
  counts when the account also behaves like a conduit, so a busy chai shop isn't a mule. When an
  account crosses the threshold, it goes on the watch-list and an alert is raised.
- **case-service** opens or updates one case per *subject* account: the payee for mule-type signals,
  otherwise the payer. Priority (P1 to P3) comes from the decision, amount, score and typology.
  New cases and KPIs are pushed to browsers over Server-Sent Events.
- Every topic is declared by every service and broker auto-create is off, which avoids a real race
  where an early consumer creates a 1-partition topic.

### 3. The feedback loop

- An analyst resolves a case as `FRAUD` or `LEGIT`. `case.labels` carries the verdict.
- `LabelConsumer` (risk-engine) deny-lists the fraudulent accounts at once (the next payment to them
  scores about 0.99) and stores the labelled feature vectors.
- `Trainer` retrains in the background: logistic regression, full-batch gradient descent, L2
  regularisation, and fraud samples weighted by √(legit/fraud), evaluated on a stratified 80/20
  hold-out. A challenger is promoted only if its AUC is no worse *and* its false-positive rate
  doesn't rise. Replicas pick up the new model through Redis.

### 4. The copilot

- The knowledge base is nine Markdown files in `case-service/src/main/resources/kb`: fraud typologies,
  RBI fraud-risk directions, customer liability, reporting and escalation, three investigation
  playbooks, UPI limits, and FraudShield's own reason codes.
- They're chunked by section and embedded in-process with all-MiniLM-L6-v2 (ONNX), then stored in
  pgvector alongside a Postgres full-text vector.
- `HybridRetriever` ranks by vector similarity and by keyword match in one SQL statement and fuses the
  rankings with Reciprocal Rank Fusion. The **Knowledge base** page shows both rankings side by side.
- With an LLM configured (`AI_PROVIDER` = `openai`, `anthropic` or `ollama`), the brief is generated
  from the case facts and the retrieved passages, with numbered citations. Without one, a
  deterministic writer builds the same structure from the evidence and playbooks.

### 5. The simulator

`txn-service` simulates 1,000 customers with homes, devices, habits and favourite merchants. On start
it back-fills 30 days of history, then streams live traffic. Attacks (`MULE_RING`, `ACCOUNT_TAKEOVER`,
`DIGITAL_ARREST`, `UPI_PROBING`) are multi-step scripts. Because the simulator knows the ground truth,
the dashboard shows an honest detection scoreboard.
