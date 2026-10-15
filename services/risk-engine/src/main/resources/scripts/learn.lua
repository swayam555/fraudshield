-- Learns the payer's trusted profile from a payment that was APPROVED: usual amount (Welford's
-- online mean and variance), last location, and the first time each device and payee was used.
-- Blocked or held payments never reach this script, so an attacker's retries can't make their
-- own device, location or amount look normal.

local profKey, devKey, payeesKey = KEYS[1], KEYS[2], KEYS[3]
local now = tonumber(ARGV[1])
local amount = tonumber(ARGV[2])
local deviceId = ARGV[3]
local lat, lon = ARGV[4], ARGV[5]
local payee = ARGV[6]

local DAY = 86400000

local prof = redis.call('HMGET', profKey, 'n', 'mean', 'm2')
local n = tonumber(prof[1]) or 0
local mean = tonumber(prof[2]) or 0
local m2 = tonumber(prof[3]) or 0
local n1 = n + 1
local delta = amount - mean
local mean1 = mean + delta / n1
local m21 = m2 + delta * (amount - mean1)
redis.call('HSET', profKey, 'n', n1, 'mean', tostring(mean1), 'm2', tostring(m21))
if lat ~= '' and lon ~= '' then
    redis.call('HSET', profKey, 'lat', lat, 'lon', lon, 'ts', now)
end
redis.call('HSETNX', profKey, 'first', now)
redis.call('PEXPIRE', profKey, 90 * DAY)

redis.call('HSETNX', devKey, deviceId, now)
redis.call('PEXPIRE', devKey, 90 * DAY)
redis.call('HSETNX', payeesKey, payee, now)
redis.call('PEXPIRE', payeesKey, 90 * DAY)
return 1
