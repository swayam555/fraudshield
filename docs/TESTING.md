# Testing FraudShield

There are four levels: automated tests (no browser), a one-command API smoke test against the running
stack, Playwright browser tests of whole user journeys, and a manual click-through for demos and design reviews.

## 1. Automated tests

```bash
./mvnw verify          # needs Docker for Testcontainers
```

31 tests, including:

| Test | What it proves |
|---|---|
| `FeatureExtractorTest` (8) | Everyday payments score low and takeovers high; pass-through vs. partly spending a transfer; a busy merchant isn't a mule; a device stays "new" for a day; late night in IST; distance maths |
| `FeatureStoreIT` (5, real Redis) | The Lua feature script counts distinct payers into a mule account, tracks velocity, usual amount and pass-through, remembers first-seen devices and payees, counts accounts sharing a device, and **never learns from blocked attempts** (an attacker's retries stay as unusual as the first) |
| `TrainerTest` (3) | The trainer learns a signal the baseline ignores, refuses to train without both classes, and round-trips samples |
| `HybridRetrieverIT` (3, real pgvector) | Exact regulatory terms are found lexically, paraphrases semantically, and re-ingestion is idempotent |
| `CaseRulesTest`, `PriorityTest` | Which account a case is about, typology, that a scam victim is never deny-listed, and P1/P2/P3 priority |
| `ChunkingTest`, `CopilotTextTest` | Knowledge-base chunking, and the copilot's fallback brief and citations |

## 2. Smoke test (stack running)

```bash
docker compose up -d --build
./scripts/smoke-test.sh                 # or: ./scripts/smoke-test.sh http://some-host
```

It prints `ok` or `FAIL` for each of 16 checks: sign-in, back-fill and live traffic, an everyday payment
approved, idempotency, an account takeover stopped with reasons, validation, a mule-ring attack opening
cases, the copilot's cited briefs, hybrid search, the model card, and the copilot rate limit.

## 3. Browser tests (Playwright)

```bash
docker compose up -d --build
cd e2e
npm install
npx playwright install chromium          # or set CHROME_PATH=/usr/bin/google-chrome to use an installed Chrome
npm test                                  # BASE_URL defaults to http://localhost:3001
npm run report                            # HTML report, with a trace and screenshot for any failure
```

`tests/journey.spec.ts` drives the 17-step analyst journey: landing page, sign-in (including a wrong password), live stream, an attack, cases, the network graph, a cited copilot brief, stolen-phone and everyday payments, confirming fraud on a mule and seeing the next payment to it blocked, the model and the knowledge base. Each step is a named `test.step`, so the report shows exactly where
something broke. A second test opens every page at 390 px and fails if any of them scrolls sideways.
Uncaught page errors and any 5xx response fail the run.

## 4. Manual test script

Open <http://localhost:3001>. The landing page's feed and animations play on their own; **Open the console** and sign in as `analyst` / `fraudshield` (or `lead` / `fraudshield`).

### A. Live monitor

| Step | Do | You should see |
|---|---|---|
| 1 | Press **Start traffic** | A warm-up message while 30 days of history are back-filled, then live payments streaming in the feed |
| 2 | Watch the tiles | Throughput of around 8 payments/s, decision latency p95 in the low tens of milliseconds, most payments green (approved) |
| 3 | **Launch an attack** → Mule ring | Within seconds, amber (review) and red (block) payments appear and the scoreboard counts what was caught |

### B. Try a payment yourself

| Step | Do | You should see |
|---|---|---|
| 4 | **Try a payment** → preset "Everyday payment…" → **Pay ₹…** | **APPROVE**, with a small risk score |
| 5 | Preset "Phone stolen, drained from…" → **Pay** | **BLOCK**, with reasons like "new device", "location jump faster than physically possible", "amount far above usual" and a contribution chart |
| 6 | Preset "'CBI officer' on a video call" → **Pay** | Held or blocked even though the device and city are the customer's own: the amount and the new payee give it away |
| 7 | Send the everyday preset 5 times quickly | The score creeps up as velocity signals build |

### C. Investigate a case

| Step | Do | You should see |
|---|---|---|
| 8 | **Cases** | A queue sorted by priority (P1 first) with SLA timers |
| 9 | Open a P1 case | Flagged payments, the payment network graph (hover an account to trace its payments; click to re-centre), a timeline, and the simulator's ground truth |
| 10 | Copilot → **Generate brief** (or **Regenerate brief**) | A summary, likely typology, recommended actions, an STR recommendation, and numbered citations you can expand |
| 11 | Open a **Money mule** case → **Assign to me** → **Confirm fraud** | The case is resolved and the mule account is deny-listed: on **Try a payment**, a payment to that account now scores about 0.99. (Scam-victim cases never deny-list the victim.) |

### D. Model and knowledge base

| Step | Do | You should see |
|---|---|---|
| 12 | **Risk model** | Feature weights, AUC, recall, precision, a confusion matrix |
| 13 | Press **Retrain now** | A challenger is trained and either promoted or rejected, with the reason |
| 14 | Move the review threshold down and send a payment | More payments are held for review |
| 15 | **Knowledge base** → ask "someone forwarding stolen money" and then "STR deadline" | Semantic search ranks the first well; keyword search nails the second; the fused ranking gets both |

### E. Resilience spot check (terminal)

Stop the risk engine and pay: small payments are approved (fail open), large ones are held (fail
closed), and both are marked `degraded`.

```bash
TOKEN=$(curl -s localhost:3001/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"analyst","password":"fraudshield"}' | jq -r .accessToken)
pay() { curl -s localhost:3001/api/payments -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"payerVpa\":\"test.user@okaxis\",\"payeeVpa\":\"shop.one@ybl\",\"amount\":$1,\"deviceId\":\"dev-x\"}" \
  | jq -c '{decision, degraded, reasons: [.reasons[].description]}'; }

docker compose stop risk-engine
pay 500       # {"decision":"APPROVE","degraded":true,"reasons":["Risk engine unavailable: small payment approved under the fail-open policy"]}
pay 25000     # {"decision":"REVIEW","degraded":true,"reasons":["Risk engine unavailable: payment above the fail-open limit held for review"]}
docker compose start risk-engine
```

## Resetting

```bash
docker compose down -v      # wipes all data; the next "Start traffic" back-fills again
```
