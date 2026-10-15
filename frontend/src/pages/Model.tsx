import { useEffect, useState } from 'react'
import { Bar, BarChart, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { RefreshCw, RotateCcw } from 'lucide-react'
import { PageHeader } from '../components/Layout'
import { Badge, Button, Card, ErrorNote, Stat, CHART, StatGrid } from '../components/ui'
import { api, post } from '../api'

interface ModelInfo {
  version: string; bias: number; trainedAt: string; metrics: Record<string, number>; labelledSamples: number
  features: { name: string; description: string; weight: number }[]
  history: { version: string; trainedAt: string; metrics: Record<string, number> }[]
}
interface Report { promoted: boolean; previousVersion: string; previousMetrics: Record<string, number>; samples: number; fraudSamples: number
  model: { version: string; metrics: Record<string, number> } }

const pct = (v?: number) => v === undefined ? '-' : `${(v * 100).toFixed(1)}%`

export default function Model() {
  const [model, setModel] = useState<ModelInfo | null>(null)
  const [thresholds, setThresholds] = useState({ reviewThreshold: 0.5, blockThreshold: 0.85 })
  const [report, setReport] = useState<Report | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const load = () => {
    api<ModelInfo>('/api/risk/model').then(setModel).catch(e => setError(e.message))
    api<typeof thresholds>('/api/risk/thresholds').then(setThresholds).catch(() => {})
  }
  useEffect(load, [])

  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError(null)
    try { await fn(); load() } catch (e) { setError((e as Error).message) } finally { setBusy(false) }
  }

  const m = model?.metrics ?? {}
  return (
    <>
      <PageHeader title="Risk model"
        subtitle="An explainable logistic-regression model over 17 real-time behavioural features. Analyst verdicts and simulator ground truth become training data; a retrained model is promoted only if it ranks at least as well without raising false positives (champion/challenger)."
        action={<div className="flex gap-2">
          <Button variant="secondary" disabled={busy} onClick={() => run(() => post('/api/risk/model/reset'))}><RotateCcw className="h-4 w-4" /> Reset to baseline</Button>
          <Button disabled={busy} onClick={() => run(async () => setReport(await post<Report>('/api/risk/model/train')))}><RefreshCw className="h-4 w-4" />{busy ? 'Training…' : 'Retrain now'}</Button>
        </div>} />
      <ErrorNote error={error} />
      {report && (
        <div className={`mb-4 rounded-lg px-4 py-3 text-sm ${report.promoted ? 'bg-approve/10 text-approve' : 'bg-review/10 text-review'}`}>
          Trained <b>{report.model.version}</b> on {report.samples.toLocaleString('en-IN')} labelled payments ({report.fraudSamples} fraud).
          AUC {report.previousMetrics.auc?.toFixed(3)} → <b>{report.model.metrics.auc?.toFixed(3)}</b>,
          recall {pct(report.previousMetrics.recall)} → <b>{pct(report.model.metrics.recall)}</b>,
          false positives {pct(report.previousMetrics.falsePositiveRate)} → <b>{pct(report.model.metrics.falsePositiveRate)}</b>.
          {report.promoted ? ' Promoted to production: every risk-engine replica hot-swaps within 5 seconds.' : ' Not promoted: it did not beat the current model.'}
        </div>
      )}
      <StatGrid className="grid-cols-2 lg:grid-cols-5">
        <Stat label="Active model" value={<span className="text-lg">{model?.version}</span>} hint={model?.trainedAt && model.trainedAt > '1971' ? `trained ${new Date(model.trainedAt).toLocaleString('en-IN')}` : 'expert-set baseline weights'} tone="indigo" />
        <Stat label="ROC AUC" value={m.auc?.toFixed(3) ?? '-'} hint="hold-out set" />
        <Stat label="Recall" value={pct(m.recall)} hint="share of fraud caught" tone="green" />
        <Stat label="Precision" value={pct(m.precision)} hint="flags that were real fraud" />
        <Stat label="Labelled samples" value={(model?.labelledSamples ?? 0).toLocaleString('en-IN')} hint="training pool in Redis" />
      </StatGrid>
      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Card title="Feature weights" className="xl:col-span-2">
          <div className="h-[460px]">
            <ResponsiveContainer>
              <BarChart data={model?.features ?? []} layout="vertical" margin={{ left: 60 }}>
                <XAxis type="number" tick={CHART.tick} />
                <YAxis type="category" dataKey="name" width={150} tick={{ ...CHART.tick, fontSize: 10 }} />
                <ReferenceLine x={0} stroke="#2d343e" />
                <Tooltip {...CHART.tooltip} formatter={(v) => Number(v).toFixed(3)} labelFormatter={(n) => model?.features.find(f => f.name === n)?.description ?? String(n)} />
                <Bar dataKey="weight" radius={[0, 4, 4, 0]}>
                  {(model?.features ?? []).map(f => <Cell key={f.name} fill={f.weight < 0 ? '#3ecf8e' : '#5b9bff'} />)}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
          <p className="text-xs text-muted">Green = lowers risk. Bias (intercept) {model?.bias.toFixed(2)}.</p>
        </Card>
        <div className="space-y-4">
          <Card title="Decision thresholds">
            <p className="mb-3 text-xs text-muted">Risk ops can tighten these during a scam wave. Stored in Redis, picked up by all replicas.</p>
            <label className="block text-xs">Hold for review at ≥ <b>{thresholds.reviewThreshold.toFixed(2)}</b>
              <input type="range" min={0.1} max={0.9} step={0.01} value={thresholds.reviewThreshold} className="w-full accent-amber-500"
                onChange={e => setThresholds(t => ({ ...t, reviewThreshold: +e.target.value }))} /></label>
            <label className="mt-2 block text-xs">Block at ≥ <b>{thresholds.blockThreshold.toFixed(2)}</b>
              <input type="range" min={0.2} max={1} step={0.01} value={thresholds.blockThreshold} className="w-full accent-red-500"
                onChange={e => setThresholds(t => ({ ...t, blockThreshold: +e.target.value }))} /></label>
            <Button className="mt-3" variant="secondary" disabled={busy}
              onClick={() => run(() => api('/api/risk/thresholds', { method: 'PUT', body: JSON.stringify(thresholds) }))}>Save thresholds</Button>
          </Card>
          <Card title="Confusion matrix (hold-out)">
            {m.tp === undefined ? <p className="text-xs text-muted">Baseline model: retrain to evaluate.</p> : (
              <table className="w-full text-center text-sm">
                <thead className="text-xs text-muted"><tr><th></th><th>Flagged</th><th>Approved</th></tr></thead>
                <tbody>
                  <tr><td className="text-xs text-muted">Fraud</td><td className="bg-approve/10 py-2 font-semibold">{m.tp}</td><td className="bg-block/10 font-semibold">{m.fn}</td></tr>
                  <tr><td className="text-xs text-muted">Genuine</td><td className="bg-review/10 py-2 font-semibold">{m.fp}</td><td className="bg-surface-2 font-semibold">{m.tn}</td></tr>
                </tbody>
              </table>
            )}
          </Card>
          <Card title="Model history">
            {(model?.history.length ?? 0) === 0 ? <p className="text-xs text-muted">No promoted models yet.</p> : (
              <ul className="space-y-1.5 text-xs">{model!.history.map(h => (
                <li key={h.version} className="flex justify-between"><span><Badge tone="indigo">{h.version}</Badge></span>
                  <span className="text-muted">AUC {h.metrics.auc?.toFixed(3)} · recall {pct(h.metrics.recall)}</span></li>))}</ul>
            )}
          </Card>
        </div>
      </div>
    </>
  )
}
