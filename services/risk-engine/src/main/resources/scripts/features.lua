-- Fetch-and-update the real-time features for one payment in a single round trip.
--
-- The script first READS every signal as it was *before* this payment, then RECORDS
-- the attempt so the next one sees it. Running both halves atomically means two
-- concurrent payments from the same account can never both see a stale count.
--
-- Only *attempt* signals are recorded here (velocity, fan-in/out, money flows, probing,
-- shared devices): attempts are exactly what they measure. The payer's *trusted* profile
-- (usual amount, last location, known devices and payees) is learned by learn.lua, and
-- only from approved payments; otherwise every blocked attack would teach the model that
-- the attacker's phone, city and amount are normal, making the next attempt look safe.
--
-- Values are returned as strings: Redis truncates Lua numbers to integers.

local velKey, profKey, devKey, payeesKey = KEYS[1], KEYS[2], KEYS[3], KEYS[4]
local inNow, inPrev, outNow, outPrev = KEYS[5], KEYS[6], KEYS[7], KEYS[8]
local payerInflow, payeeInflow, payerOutflow = KEYS[9], KEYS[10], KEYS[11]
local devAccKey, microKey, payeeProfKey, denyKey, watchKey = KEYS[12], KEYS[13], KEYS[14], KEYS[15], KEYS[16]
local payeeOutflow = KEYS[17]

local txnId = ARGV[1]
local now = tonumber(ARGV[2])
local amount = tonumber(ARGV[3])
local deviceId = ARGV[4]
local lat, lon = ARGV[5], ARGV[6]
local payer, payee = ARGV[7], ARGV[8]
local microLimit = tonumber(ARGV[9])

local MIN, HOUR, DAY = 60000, 3600000, 86400000

-- ---------- read ----------
local cnt1m = redis.call('ZCOUNT', velKey, now - MIN, '+inf')
local cnt1h = redis.call('ZCOUNT', velKey, now - HOUR, '+inf')

local prof = redis.call('HMGET', profKey, 'n', 'mean', 'm2', 'lat', 'lon', 'ts', 'first')
local n = tonumber(prof[1]) or 0
local mean = tonumber(prof[2]) or 0
local m2 = tonumber(prof[3]) or 0

-- Devices and payees are hashes of "first seen" timestamps: a device bound ten minutes ago is
-- still risky even though it is no longer strictly new.
local devCount = redis.call('HLEN', devKey)
local devFirst = redis.call('HGET', devKey, deviceId)
local payeeFirstPaid = redis.call('HGET', payeesKey, payee)

local fanIn = redis.call('PFCOUNT', inNow, inPrev)
local fanOut = redis.call('PFCOUNT', outNow, outPrev)

local function windowSum(key, from)
    local total = 0
    for _, member in ipairs(redis.call('ZRANGEBYSCORE', key, from, '+inf')) do
        local sep = string.find(member, '|', 1, true)
        total = total + (tonumber(string.sub(member, sep + 1)) or 0)
    end
    return total
end
local inflow30 = windowSum(payerInflow, now - 30 * MIN)
local outflow30 = windowSum(payerOutflow, now - 30 * MIN)
local payeeOut1h = windowSum(payeeOutflow, now - HOUR)

local devAccounts = redis.call('SCARD', devAccKey)
if redis.call('SISMEMBER', devAccKey, payer) == 0 then devAccounts = devAccounts + 1 end
local micro10 = redis.call('ZCOUNT', microKey, now - 10 * MIN, '+inf')
local payeeFirst = redis.call('HGET', payeeProfKey, 'first')
local denied = redis.call('SISMEMBER', denyKey, payee)
local watch = redis.call('ZSCORE', watchKey, payee)

-- ---------- record ----------
redis.call('ZADD', velKey, now, txnId)
redis.call('ZREMRANGEBYSCORE', velKey, '-inf', now - DAY)
redis.call('PEXPIRE', velKey, DAY)

redis.call('HSETNX', profKey, 'first', now)
redis.call('PEXPIRE', profKey, 90 * DAY)
redis.call('HSETNX', payeeProfKey, 'first', now)
redis.call('PEXPIRE', payeeProfKey, 90 * DAY)

redis.call('SADD', devAccKey, payer)
redis.call('PEXPIRE', devAccKey, 30 * DAY)

redis.call('PFADD', inNow, payer)
redis.call('PEXPIRE', inNow, 2 * HOUR)
redis.call('PFADD', outNow, payee)
redis.call('PEXPIRE', outNow, 2 * HOUR)

local member = txnId .. '|' .. tostring(amount)
redis.call('ZADD', payeeInflow, now, member)
redis.call('ZREMRANGEBYSCORE', payeeInflow, '-inf', now - HOUR)
redis.call('PEXPIRE', payeeInflow, HOUR)
redis.call('ZADD', payerOutflow, now, member)
redis.call('ZREMRANGEBYSCORE', payerOutflow, '-inf', now - HOUR)
redis.call('PEXPIRE', payerOutflow, HOUR)

if amount <= microLimit then
    redis.call('ZADD', microKey, now, txnId)
    redis.call('ZREMRANGEBYSCORE', microKey, '-inf', now - HOUR)
    redis.call('PEXPIRE', microKey, HOUR)
end

return {
    tostring(cnt1m), tostring(cnt1h), tostring(n), tostring(mean), tostring(m2),
    prof[4] or '', prof[5] or '', prof[6] or '', devFirst or '', tostring(devCount),
    payeeFirstPaid or '', tostring(fanIn), tostring(fanOut), tostring(inflow30), tostring(outflow30),
    tostring(devAccounts), tostring(micro10), payeeFirst or '', prof[7] or '', tostring(denied),
    watch or '0', tostring(payeeOut1h)
}
