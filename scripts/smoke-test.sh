#!/usr/bin/env bash
# End-to-end smoke test against a running stack:  ./scripts/smoke-test.sh [base-url]
# Needs curl, jq and uuidgen. Starts synthetic traffic if it isn't running. Takes about a minute.
set -uo pipefail
B=${1:-http://localhost:3001}
FAIL=0
j() { curl -s -H 'Content-Type: application/json' "$@"; }
check() { if [ "$2" = "$3" ]; then echo "  ok   $1"; else echo "  FAIL $1 (expected $3, got $2)"; FAIL=1; fi; }

echo "Sign-in"
T=$(j $B/api/auth/login -d '{"username":"analyst","password":"fraudshield"}' | jq -r .accessToken)
H="Authorization: Bearer $T"
check "wrong password is rejected" "$(j -o /dev/null -w '%{http_code}' $B/api/auth/login -d '{"username":"analyst","password":"nope"}')" 401
check "no token is rejected" "$(curl -s -o /dev/null -w '%{http_code}' $B/api/cases)" 401

echo "Traffic"
j -X POST $B/api/simulator/start -H "$H" -d '{}' >/dev/null
for _ in $(seq 90); do
  [ "$(curl -s $B/api/simulator -H "$H" | jq .warmedUp)" = true ] && break; sleep 2
done
check "history back-filled and live traffic flowing" "$(curl -s $B/api/simulator -H "$H" | jq .warmedUp)" true

echo "Scoring"
C=$(curl -s $B/api/simulator/demo-customer -H "$H")
vpa=$(echo "$C" | jq -r .vpa); dev=$(echo "$C" | jq -r .deviceId); lat=$(echo "$C" | jq .lat); lon=$(echo "$C" | jq .lon)
merchant=$(echo "$C" | jq -r .favouriteMerchant); typical=$(echo "$C" | jq .typicalAmount)
K=$(uuidgen)
ok=$(j $B/api/payments -H "$H" -H "Idempotency-Key: $K" -d "{\"payerVpa\":\"$vpa\",\"payeeVpa\":\"$merchant\",\"amount\":$typical,\"deviceId\":\"$dev\",\"lat\":$lat,\"lon\":$lon}")
again=$(j $B/api/payments -H "$H" -H "Idempotency-Key: $K" -d "{\"payerVpa\":\"$vpa\",\"payeeVpa\":\"$merchant\",\"amount\":$typical,\"deviceId\":\"$dev\",\"lat\":$lat,\"lon\":$lon}")
check "everyday payment is approved" "$(echo "$ok" | jq -r .decision)" APPROVE
check "same Idempotency-Key returns the same payment" "$(echo "$again" | jq -r .txnId)" "$(echo "$ok" | jq -r .txnId)"
ato=$(j $B/api/payments -H "$H" -d "{\"payerVpa\":\"$vpa\",\"payeeVpa\":\"quickcash$RANDOM@ybl\",\"amount\":74000,\"deviceId\":\"stolen-$RANDOM\",\"lat\":$(echo "$C" | jq .farLat),\"lon\":$(echo "$C" | jq .farLon)}")
check "account takeover is not approved" "$(echo "$ato" | jq -r '.decision != "APPROVE"')" true
check "decision explains itself" "$(echo "$ato" | jq '.reasons | length > 0')" true
check "invalid UPI ID is refused" "$(j -o /dev/null -w '%{http_code}' $B/api/payments -H "$H" -d '{"payerVpa":"x","payeeVpa":"y@ybl","amount":10,"deviceId":"d"}')" 422

echo "Cases and copilot"
j -X POST $B/api/simulator/inject/MULE_RING -H "$H" >/dev/null
sleep 15
CID=$(curl -s "$B/api/cases" -H "$H" | jq -r 'if type=="array" then .[0].id else .content[0].id end')
check "cases are opened" "$([ "$CID" != null ] && echo yes)" yes
check "case detail lists its payments" "$(curl -s $B/api/cases/$CID -H "$H" | jq -r '.kase.id != null and (.payments | length > 0)')" true
check "copilot brief cites sources" "$(j -X POST $B/api/copilot/cases/$CID/summary -H "$H" | jq '.citations | length > 0')" true
check "policy answer cites sources" "$(j $B/api/copilot/ask -H "$H" -d '{"question":"When must we file an STR?"}' | jq '.citations | length > 0')" true
check "hybrid search finds STR guidance" "$(curl -s "$B/api/kb/search?q=STR%20deadline" -H "$H" | jq 'length > 0')" true

echo "Model"
check "model card is published" "$(curl -s $B/api/risk/model -H "$H" | jq 'has("auc") or has("metrics") or has("version")')" true

echo "Rate limiting"
codes=""; for _ in $(seq 10); do codes="$codes $(j -o /dev/null -w '%{http_code}' $B/api/copilot/ask -H "$H" -d '{"question":"x"}')"; done
check "a burst of copilot calls gets HTTP 429" "$(echo "$codes" | grep -q 429 && echo yes)" yes

[ $FAIL = 0 ] && echo "All checks passed." || { echo "Some checks failed."; exit 1; }
