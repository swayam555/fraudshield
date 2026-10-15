import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, CheckCircle2, ShieldAlert, UserCheck } from 'lucide-react'
import { Badge, Button, Card, DecisionBadge, Empty, ErrorNote, PriorityBadge, ReasonChips } from '../components/ui'
import GraphView from '../components/GraphView'
import Copilot from '../components/Copilot'
import { StatusBadge } from './Cases'
import { api, inr, post, SCENARIO_LABEL, timeAgo, type CaseDetail as Detail, type Graph } from '../api'

export default function CaseDetail() {
  const { id } = useParams()
  const [detail, setDetail] = useState<Detail | null>(null)
  const [graph, setGraph] = useState<Graph | null>(null)
  const [graphRoot, setGraphRoot] = useState<string | null>(null)
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => api<Detail>(`/api/cases/${id}`).then(d => {
    setDetail(d)
    setGraphRoot(r => r ?? d.kase.subjectVpa)
  }).catch(e => setError(e.message)), [id])

  useEffect(() => { load() }, [load])
  useEffect(() => {
    if (graphRoot) api<Graph>(`/api/risk/graph/${encodeURIComponent(graphRoot)}?depth=2`).then(setGraph).catch(() => {})
  }, [graphRoot])

  const act = async (fn: () => Promise<Detail>) => {
    setBusy(true); setError(null)
    try { setDetail(await fn()) } catch (e) { setError((e as Error).message) } finally { setBusy(false) }
  }

  if (!detail) return error ? <ErrorNote error={error} /> : <Empty>Loading case…</Empty>
  const c = detail.kase
  const open = c.status === 'OPEN' || c.status === 'IN_REVIEW'

  return (
    <>
      <Link to="/app/cases" className="mb-3 inline-flex items-center gap-1 text-sm text-muted hover:text-fg"><ArrowLeft className="h-4 w-4" /> All cases</Link>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-semibold text-fg">Case #{c.id}</h1>
            <PriorityBadge priority={c.priority} /><StatusBadge status={c.status} />
            {c.groundTruth && <Badge tone="slate">simulator truth: {SCENARIO_LABEL[c.groundTruth] ?? c.groundTruth}</Badge>}
          </div>
          <div className="mt-1 font-mono text-sm text-fg-2">{c.subjectVpa}</div>
          <div className="mt-1 text-sm text-muted">
            {detail.typologyLabel} · {c.paymentCount} flagged payment(s) · {inr(c.totalAmount)} · highest risk {c.riskScore.toFixed(2)} · opened {timeAgo(c.createdAt)}
            {c.assignee && <> · assigned to <b>{c.assignee}</b></>}
          </div>
        </div>
        {open && (
          <div className="flex flex-wrap gap-2">
            {c.status === 'OPEN' && <Button variant="secondary" disabled={busy} onClick={() => act(() => post(`/api/cases/${id}/assign`))}><UserCheck className="h-4 w-4" /> Assign to me</Button>}
            <Button variant="success" disabled={busy} onClick={() => act(() => post(`/api/cases/${id}/resolve`, { verdict: 'LEGIT', note }))}><CheckCircle2 className="h-4 w-4" /> Genuine (false positive)</Button>
            <Button variant="danger" disabled={busy} onClick={() => act(() => post(`/api/cases/${id}/resolve`, { verdict: 'FRAUD', note }))}><ShieldAlert className="h-4 w-4" /> Confirm fraud</Button>
          </div>
        )}
      </div>
      <ErrorNote error={error} />

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-5">
        <div className="space-y-4 xl:col-span-3">
          <Card title="Payment network" action={graphRoot !== c.subjectVpa &&
            <button className="text-xs text-signal" onClick={() => setGraphRoot(c.subjectVpa)}>Back to subject</button>}>
            {graph && graph.nodes.length > 1 ? <GraphView graph={graph} onSelect={setGraphRoot} />
              : <Empty>No payment relationships recorded for this account yet.</Empty>}
            <p className="mt-2 text-[11px] text-faint">Click an account to re-centre the graph on it. Built from Kafka events by the risk engine's graph analyser.</p>
          </Card>

          <Card title={`Flagged payments (${detail.payments.length})`}>
            {detail.payments.length === 0 ? <Empty>This case was opened by a graph alert; see the timeline.</Empty> : (
              <div className="overflow-x-auto"><table className="w-full min-w-[720px] text-sm">
                <thead className="text-left text-xs text-muted"><tr><th className="py-1.5 pr-4">When</th><th className="pr-4">From → To</th><th className="pr-4 text-right">Amount</th><th className="pr-4">Decision</th><th>Why</th></tr></thead>
                <tbody className="divide-y divide-line">
                  {detail.payments.map(p => (
                    <tr key={p.txnId} className="align-top">
                      <td className="whitespace-nowrap py-2.5 pr-4 text-xs text-muted">{new Date(p.paidAt).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })}</td>
                      <td className="max-w-52 py-2.5 pr-4 font-mono text-xs"><div className="truncate">{p.payerVpa}</div><div className="truncate text-faint">→ {p.payeeVpa}</div><div className="text-[10px] text-faint">{p.deviceId}</div></td>
                      <td className="num whitespace-nowrap py-2.5 pr-4 text-right">{inr(p.amount)}</td>
                      <td className="py-2.5 pr-4"><DecisionBadge decision={p.decision} /><div className="mt-1 font-mono text-xs tabular-nums text-muted">{p.riskScore.toFixed(2)}</div></td>
                      <td className="max-w-xs py-2.5">
                        <ul className="space-y-0.5 text-xs text-fg-2">
                          {p.reasons.slice(0, 3).map(r => <li key={r.code}>• {r.description}</li>)}
                        </ul>
                        <div className="mt-1"><ReasonChips reasons={p.reasons} max={6} /></div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table></div>
            )}
          </Card>

          <Card title="Timeline">
            <ol className="space-y-3">
              {detail.timeline.map(e => (
                <li key={e.id} className="flex gap-3 text-sm">
                  <div className="w-28 shrink-0 text-xs text-faint">{new Date(e.at).toLocaleTimeString('en-IN')}</div>
                  <div><Badge tone={e.eventType === 'RESOLVED' ? 'green' : e.eventType === 'AI_SUMMARY' ? 'indigo' : 'slate'}>{e.eventType}</Badge>
                    <span className="ml-2 text-xs text-muted">{e.actor}</span>
                    <div className="mt-0.5 text-fg-2">{e.message}</div></div>
                </li>
              ))}
            </ol>
            {open && (
              <form className="mt-4 flex gap-2" onSubmit={ev => { ev.preventDefault(); if (note.trim()) act(() => post(`/api/cases/${id}/notes`, { text: note })).then(() => setNote('')) }}>
                <input value={note} onChange={e => setNote(e.target.value)} placeholder="Add a note (also attached to the verdict)…" className="flex-1 rounded-lg border border-line-2 px-3 py-1.5 text-sm" />
                <Button type="submit" variant="secondary" disabled={busy}>Add note</Button>
              </form>
            )}
          </Card>
        </div>

        <div className="xl:col-span-2">
          <Card><Copilot caseId={c.id} initialSummary={c.aiSummary} initialBy={c.aiSummaryBy} /></Card>
        </div>
      </div>
    </>
  )
}
