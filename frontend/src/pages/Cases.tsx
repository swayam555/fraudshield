import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Clock } from 'lucide-react'
import clsx from 'clsx'
import { PageHeader } from '../components/Layout'
import { Badge, Card, Empty, ErrorNote, PriorityBadge, RiskBar, Stat, StatGrid } from '../components/ui'
import { api, compactInr, inr, timeAgo, TYPOLOGY_LABEL, type CaseRow } from '../api'

interface Stats {
  queue: { open: number; inReview: number; resolvedFraud: number; resolvedLegit: number; p1Open: number; breachedSla: number }
  typologies24h: { typology: string; cases: number; amount: number }[]
}

const STATUS_TABS = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'RESOLVED', label: 'Resolved' },
  { value: '', label: 'All' },
]

export default function Cases() {
  const [status, setStatus] = useState('ACTIVE')
  const [priority, setPriority] = useState('')
  const [typology, setTypology] = useState('')
  const [q, setQ] = useState('')
  const [rows, setRows] = useState<CaseRow[]>([])
  const [stats, setStats] = useState<Stats | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const load = () => {
      const params = new URLSearchParams({ status, priority, typology, q, limit: '100' })
      api<CaseRow[]>(`/api/cases?${params}`).then(setRows).catch(e => setError(e.message))
      api<Stats>('/api/cases/stats').then(setStats).catch(() => {})
    }
    load()
    const t = setInterval(load, 5000)
    return () => clearInterval(t)
  }, [status, priority, typology, q])

  return (
    <>
      <PageHeader title="Investigation cases"
        subtitle="Held and blocked payments are grouped into one case per account; mule alerts from the graph analyser open cases too." />
      <StatGrid className="grid-cols-2 lg:grid-cols-5">
        <Stat label="Open" value={stats?.queue.open ?? 0} />
        <Stat label="In review" value={stats?.queue.inReview ?? 0} tone="indigo" />
        <Stat label="P1 open" value={stats?.queue.p1Open ?? 0} tone="red" hint="15-minute SLA" />
        <Stat label="SLA breached" value={stats?.queue.breachedSla ?? 0} tone="amber" />
        <Stat label="Confirmed fraud" value={stats?.queue.resolvedFraud ?? 0} tone="green"
          hint={`${stats?.queue.resolvedLegit ?? 0} cleared as false positives`} />
      </StatGrid>

      <Card className="mt-4" title={
        <div className="flex gap-1">
          {STATUS_TABS.map(t => (
            <button key={t.value} onClick={() => setStatus(t.value)}
              className={clsx('rounded-md px-2.5 py-1 text-xs font-medium', status === t.value ? 'bg-surface/10 text-fg' : 'text-fg-2 hover:bg-white/5')}>
              {t.label}
            </button>
          ))}
        </div>
      } action={
        <div className="flex flex-wrap gap-2">
          <input placeholder="Search UPI ID or case #" value={q} onChange={e => setQ(e.target.value)}
            className="w-full rounded-lg border border-line-2 px-2.5 py-1 text-xs sm:w-56" />
          <select value={priority} onChange={e => setPriority(e.target.value)} className="rounded-lg border border-line-2 px-2 py-1 text-xs">
            <option value="">All priorities</option><option>P1</option><option>P2</option><option>P3</option>
          </select>
          <select value={typology} onChange={e => setTypology(e.target.value)} className="rounded-lg border border-line-2 px-2 py-1 text-xs">
            <option value="">All patterns</option>
            {Object.entries(TYPOLOGY_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
      }>
        <ErrorNote error={error} />
        {rows.length === 0 ? <Empty>No cases match. Start the simulator on the Live monitor to generate traffic.</Empty> : (
          <div className="overflow-x-auto"><table className="w-full min-w-[860px] text-sm">
            <thead className="text-left text-xs text-muted">
              <tr><th className="py-2">Case</th><th>Account</th><th>Pattern</th><th>Priority</th><th>Risk</th><th>Payments</th><th>Amount</th><th>Status</th><th>Opened</th></tr>
            </thead>
            <tbody className="divide-y divide-line">
              {rows.map(c => {
                const breached = ['OPEN', 'IN_REVIEW'].includes(c.status) && new Date(c.slaDueAt) < new Date()
                return (
                  <tr key={c.id} className="hover:bg-white/[0.03]">
                    <td className="py-2"><Link to={`/app/cases/${c.id}`} className="font-medium text-signal">#{c.id}</Link></td>
                    <td className="max-w-56 truncate font-mono text-xs">{c.subjectVpa}</td>
                    <td className="text-xs">{TYPOLOGY_LABEL[c.typology]}{c.caseType === 'MULE_ALERT' && <Badge tone="indigo">graph alert</Badge>}</td>
                    <td><PriorityBadge priority={c.priority} /></td>
                    <td><RiskBar score={c.riskScore} /></td>
                    <td className="tabular-nums">{c.paymentCount}</td>
                    <td className="tabular-nums" title={inr(c.totalAmount)}>{compactInr(c.totalAmount)}</td>
                    <td><StatusBadge status={c.status} /></td>
                    <td className="text-xs text-muted">
                      <span className={clsx('flex items-center gap-1', breached && 'text-block')}>
                        {breached && <Clock className="h-3 w-3" />}{timeAgo(c.createdAt)}
                      </span>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table></div>
        )}
      </Card>
    </>
  )
}

export function StatusBadge({ status }: { status: string }) {
  const map: Record<string, [string, string]> = {
    OPEN: ['Open', 'amber'], IN_REVIEW: ['In review', 'indigo'],
    RESOLVED_FRAUD: ['Fraud confirmed', 'red'], RESOLVED_LEGIT: ['Cleared', 'green'],
  }
  const [label, tone] = map[status] ?? [status, 'slate']
  return <Badge tone={tone}>{label}</Badge>
}
