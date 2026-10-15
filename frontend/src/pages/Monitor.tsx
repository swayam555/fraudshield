import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Pause, Play, Radio, Siren, Smartphone, UserX, Fingerprint } from 'lucide-react'
import clsx from 'clsx'
import { PageHeader } from '../components/Layout'
import { Badge, Button, Card, DecisionBadge, Empty, PriorityBadge, ReasonChips, Stat, CHART, StatGrid } from '../components/ui'
import { api, compactInr, inr, post, SCENARIO_LABEL, TYPOLOGY_LABEL, type SimStatus } from '../api'
import { useLiveFeed } from '../useLiveFeed'

const ATTACKS = [
  { scenario: 'MULE_RING', label: 'Mule ring', icon: Siren, help: '10-15 victims pay new accounts that forward the money within minutes' },
  { scenario: 'ACCOUNT_TAKEOVER', label: 'Account takeover', icon: Smartphone, help: 'New phone in another city drains the account' },
  { scenario: 'DIGITAL_ARREST', label: '"Digital arrest" scam', icon: UserX, help: 'Senior citizen coerced into escalating transfers' },
  { scenario: 'UPI_PROBING', label: 'UPI ID probing', icon: Fingerprint, help: '₹1 test payments to find live UPI IDs' },
]

export default function Monitor() {
  const { stats, payments, cases, connected } = useLiveFeed()
  const [sim, setSim] = useState<SimStatus | null>(null)
  const [tps, setTps] = useState(10)
  const [fraudRate, setFraudRate] = useState(0.05)
  const [busy, setBusy] = useState(false)

  const refresh = useCallback(() => api<SimStatus>('/api/simulator').then(setSim).catch(() => {}), [])
  useEffect(() => {
    refresh()
    const t = setInterval(refresh, 2000)
    return () => clearInterval(t)
  }, [refresh])

  const act = async (fn: () => Promise<SimStatus>) => {
    setBusy(true)
    try { setSim(await fn()) } finally { setBusy(false) }
  }

  const gt = stats?.groundTruth
  return (
    <>
      <PageHeader title="Live monitor"
        subtitle="Every UPI payment is scored before it is approved. Decisions stream in over Kafka and Server-Sent Events."
        action={<span className={clsx('flex items-center gap-1.5 text-xs font-medium', connected ? 'text-approve' : 'text-faint')}>
          <Radio className="h-3.5 w-3.5" /> {connected ? 'Live' : 'Connecting…'}
        </span>} />

      <StatGrid className="grid-cols-2 lg:grid-cols-5">
        <Stat label="Throughput" value={`${stats?.tps ?? 0}/s`} hint="payments per second, last 60 s" />
        <Stat label="Decision latency" value={`${stats?.p95LatencyMs ?? 0} ms`} hint={`p95 · p50 ${stats?.p50LatencyMs ?? 0} ms · p99 ${stats?.p99LatencyMs ?? 0} ms`} tone="indigo" />
        <Stat label="Held for review" value={stats?.reviewed ?? 0} hint={`${compactInr(stats?.reviewedAmount ?? 0)} in last 60 s`} tone="amber" />
        <Stat label="Blocked" value={stats?.blocked ?? 0} hint={`${compactInr(stats?.blockedAmount ?? 0)} in last 60 s`} tone="red" />
        <Stat label="Money protected" value={compactInr(stats?.totalBlockedAmount ?? 0)} hint={`${(stats?.totalPayments ?? 0).toLocaleString('en-IN')} payments since start`} tone="green" />
      </StatGrid>

      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Card title="Decisions per second" className="xl:col-span-2">
          <div className="h-56">
            <ResponsiveContainer>
              <AreaChart data={stats?.series ?? []} margin={{ left: -20, right: 8, top: 4 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={CHART.grid} />
                <XAxis dataKey="t" tickFormatter={t => new Date(t).toLocaleTimeString('en-IN', { minute: '2-digit', second: '2-digit' })}
                  tick={CHART.tick} minTickGap={40} />
                <YAxis allowDecimals={false} tick={CHART.tick} />
                <Tooltip {...CHART.tooltip} labelFormatter={t => new Date(Number(t)).toLocaleTimeString('en-IN')} />
                <Area type="monotone" dataKey="APPROVE" stackId="1" stroke="#3ecf8e" fill="#3ecf8e" fillOpacity={0.18} isAnimationActive={false} />
                <Area type="monotone" dataKey="REVIEW" stackId="1" stroke="#f5b03d" fill="#f5b03d" fillOpacity={0.25} isAnimationActive={false} />
                <Area type="monotone" dataKey="BLOCK" stackId="1" stroke="#ff6363" fill="#ff6363" fillOpacity={0.3} isAnimationActive={false} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card title="Traffic simulator" action={sim?.warmingUp ? <Badge tone="amber">Back-filling 30 days of history…</Badge>
          : sim?.running ? <Badge tone="green">Running</Badge> : <Badge>Stopped</Badge>}>
          <div className="space-y-3">
            <label className="block text-xs text-fg-2">Payments per second: <b>{tps}</b>
              <input type="range" min={1} max={60} value={tps} onChange={e => setTps(+e.target.value)} className="w-full accent-[#5b9bff]" />
            </label>
            <label className="block text-xs text-fg-2">Attack frequency: <b>{Math.round(fraudRate * 100)}%</b>
              <input type="range" min={0} max={0.3} step={0.01} value={fraudRate} onChange={e => setFraudRate(+e.target.value)} className="w-full accent-[#5b9bff]" />
            </label>
            <div className="flex gap-2">
              {sim?.running
                ? <Button variant="secondary" disabled={busy} onClick={() => act(() => post('/api/simulator/stop'))}><Pause className="h-4 w-4" /> Stop</Button>
                : <Button disabled={busy} onClick={() => act(() => post('/api/simulator/start', { tps, fraudRate }))}><Play className="h-4 w-4" /> Start traffic</Button>}
              {sim?.running && <Button variant="secondary" disabled={busy}
                onClick={() => act(() => post('/api/simulator/start', { tps, fraudRate }))}>Apply</Button>}
            </div>
            <div className="border-t border-line pt-3">
              <div className="mb-2 text-xs font-medium text-muted">Launch an attack</div>
              <div className="grid grid-cols-2 gap-2">
                {ATTACKS.map(({ scenario, label, icon: Icon, help }) => (
                  <button key={scenario} title={help} disabled={busy}
                    onClick={() => act(() => post(`/api/simulator/inject/${scenario}`))}
                    className="flex items-center gap-1.5 rounded-lg border border-line px-2 py-1.5 text-left text-xs font-medium text-fg-2 hover:border-block/40 hover:bg-block/10">
                    <Icon className="h-3.5 w-3.5 text-block" /> {label}
                  </button>
                ))}
              </div>
              {(sim?.pendingAttackSteps ?? 0) > 0 && <div className="mt-2 text-xs text-review">{sim?.pendingAttackSteps} attack payments queued</div>}
            </div>
          </div>
        </Card>
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Card title="Live payments" className="xl:col-span-2">
          {payments.length === 0 ? <Empty>No live traffic yet. Start the simulator or send a payment from "Try a payment".</Empty> : (
            <div className="max-h-[480px] overflow-auto">
              <table className="w-full text-sm">
                <thead className="sticky top-0 bg-surface text-left text-xs text-muted">
                  <tr><th className="py-2">From → To</th><th>Amount</th><th>Decision</th><th>Risk</th><th>Signals</th><th className="text-right">Latency</th></tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {payments.map(p => (
                    <tr key={p.txnId} className="row-new">
                      <td className="max-w-64 py-1.5"><div className="truncate font-mono text-xs">{p.payer}</div>
                        <div className="truncate font-mono text-xs text-faint">→ {p.payee}</div></td>
                      <td className="tabular-nums">{inr(p.amount)}</td>
                      <td><DecisionBadge decision={p.decision} /></td>
                      <td className="tabular-nums text-xs">{p.riskScore.toFixed(2)}</td>
                      <td><ReasonChips reasons={p.decision === 'APPROVE' ? [] : p.reasons} /></td>
                      <td className="text-right text-xs tabular-nums text-muted">{p.latencyMs} ms</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>

        <div className="space-y-4">
          <Card title="Detection scoreboard">
            <p className="mb-3 text-xs text-muted">The simulator knows which payments are attacks, so we can measure the model honestly.</p>
            <div className="mb-3 grid grid-cols-2 gap-2 text-center">
              <div className="rounded-lg bg-approve/10 p-2"><div className="text-lg font-semibold text-approve">{gt?.detectionRate ?? 0}%</div><div className="text-[11px] text-approve">attacks caught (live)</div></div>
              <div className="rounded-lg bg-surface-2 p-2"><div className="text-lg font-semibold text-fg-2">{gt?.falsePositiveRate ?? 0}%</div><div className="text-[11px] text-fg-2">genuine payments flagged</div></div>
            </div>
            <table className="w-full text-xs">
              <thead className="text-muted"><tr><th className="text-left">Scenario</th><th>Caught</th><th>Missed</th></tr></thead>
              <tbody>
                {Object.entries(sim?.scoreboard ?? {}).map(([s, row]) => {
                  const flagged = (row.REVIEW ?? 0) + (row.BLOCK ?? 0)
                  return (
                    <tr key={s} className="border-t border-line">
                      <td className="py-1">{SCENARIO_LABEL[s] ?? s}</td>
                      <td className="text-center tabular-nums">{s === 'LEGIT' ? '-' : flagged}</td>
                      <td className="text-center tabular-nums">{s === 'LEGIT' ? `${flagged} flagged` : row.APPROVE ?? 0}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </Card>
          <Card title="New cases" action={<Link to="/app/cases" className="text-xs font-medium text-signal">Open queue →</Link>}>
            {cases.length === 0 ? <Empty>Cases appear here as soon as they open.</Empty> : (
              <ul className="space-y-2">
                {cases.map(c => (
                  <li key={c.id}>
                    <Link to={`/app/cases/${c.id}`} className="flex items-center justify-between gap-2 rounded-lg px-2 py-1.5 hover:bg-white/[0.03]">
                      <div className="min-w-0"><div className="truncate font-mono text-xs">{c.subject}</div>
                        <div className="text-[11px] text-muted">{TYPOLOGY_LABEL[c.typology]}</div></div>
                      <PriorityBadge priority={c.priority} />
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>
    </>
  )
}
