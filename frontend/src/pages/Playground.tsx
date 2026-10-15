import { useEffect, useState } from 'react'
import { Send } from 'lucide-react'
import { Bar, BarChart, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { PageHeader } from '../components/Layout'
import { Button, Card, DecisionBadge, ErrorNote, CHART } from '../components/ui'
import { api, inr, post, type PaymentResult } from '../api'

interface Form { payerVpa: string; payeeVpa: string; amount: number; deviceId: string; lat: number; lon: number; note: string }

interface DemoCustomer { vpa: string; deviceId: string; homeCity: string; lat: number; lon: number; typicalAmount: number
  favouriteMerchant: string; farCity: string; farLat: number; farLon: number }

/** Presets built around a real simulated customer, so every attack is judged against genuine history. */
function presets(c: DemoCustomer) {
  const suffix = Math.floor(Math.random() * 9000 + 1000)
  return [
    { label: `Everyday payment to ${c.favouriteMerchant.split('@')[0]}`, help: 'Usual amount, usual phone, usual city',
      form: { payerVpa: c.vpa, payeeVpa: c.favouriteMerchant, amount: Math.max(20, c.typicalAmount), deviceId: c.deviceId, lat: c.lat, lon: c.lon, note: '' } },
    { label: `Phone stolen, drained from ${c.farCity}`, help: 'New device far from home, large amount to a new payee',
      form: { payerVpa: c.vpa, payeeVpa: `fastpay${suffix}@ybl`, amount: 74000, deviceId: `dev-unknown-${suffix}`, lat: c.farLat, lon: c.farLon, note: '' } },
    { label: '"CBI officer" on a video call', help: 'Customer\'s own phone and home, but a huge first payment to a new payee',
      form: { payerVpa: c.vpa, payeeVpa: `cyber.cell.verification${suffix}@okaxis`, amount: 95000, deviceId: c.deviceId, lat: c.lat, lon: c.lon, note: 'Security deposit - case clearance' } },
  ]
}

/** Feature weights × values, sorted: exactly how the logistic model arrived at its score. */
export default function Playground() {
  const [customer, setCustomer] = useState<DemoCustomer | null>(null)
  const [form, setForm] = useState<Form | null>(null)
  const [result, setResult] = useState<PaymentResult | null>(null)
  const [history, setHistory] = useState<PaymentResult[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    api<DemoCustomer>('/api/simulator/demo-customer').then(c => { setCustomer(c); setForm(presets(c)[0].form) })
      .catch(e => setError((e as Error).message))
  }, [])

  const submit = async () => {
    if (!form) return
    setBusy(true); setError(null)
    try {
      const r = await post<PaymentResult>('/api/payments', form, { 'Idempotency-Key': crypto.randomUUID() })
      setResult(r); setHistory(h => [r, ...h].slice(0, 8))
    } catch (e) { setError((e as Error).message) } finally { setBusy(false) }
  }
  const set = (k: keyof Form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm(f => f && ({ ...f, [k]: e.target.type === 'number' ? +e.target.value : e.target.value }))

  if (!form || !customer) return <><PageHeader title="Try a payment" /><ErrorNote error={error} />{!error && <p className="text-sm text-muted">Loading a demo customer…</p>}</>

  return (
    <>
      <PageHeader title="Try a payment"
        subtitle="Send a UPI payment through the real pipeline (gateway → payment service → risk engine → Kafka) and see why it was approved, held or blocked. Try sending the same preset several times: velocity and new-payee signals change." />
      <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        <Card title="Payment">
          <p className="mb-3 text-xs text-muted">Paying as <b className="font-mono">{customer.vpa}</b>, who lives in {customer.homeCity}, usually spends about {inr(customer.typicalAmount)}{' '}
            and pays from <span className="font-mono">{customer.deviceId}</span>.</p>
          <div className="mb-4 flex flex-wrap gap-2">
            {presets(customer).map(p => (
              <button key={p.label} title={p.help} onClick={() => setForm(p.form)}
                className="rounded-full border border-line px-3 py-1 text-xs font-medium text-fg-2 hover:border-signal/50 hover:bg-signal/10">{p.label}</button>
            ))}
          </div>
          <div className="grid grid-cols-2 gap-3 text-sm">
            <Field label="Payer UPI ID"><input value={form.payerVpa} onChange={set('payerVpa')} className="input" /></Field>
            <Field label="Payee UPI ID"><input value={form.payeeVpa} onChange={set('payeeVpa')} className="input" /></Field>
            <Field label="Amount (₹)"><input type="number" value={form.amount} onChange={set('amount')} className="input" /></Field>
            <Field label="Device ID"><input value={form.deviceId} onChange={set('deviceId')} className="input" /></Field>
            <Field label="Latitude"><input type="number" step="0.01" value={form.lat} onChange={set('lat')} className="input" /></Field>
            <Field label="Longitude"><input type="number" step="0.01" value={form.lon} onChange={set('lon')} className="input" /></Field>
            <Field label="Note" wide><input value={form.note} onChange={set('note')} className="input" /></Field>
          </div>
          <div className="mt-4 flex items-center gap-3">
            <Button onClick={submit} disabled={busy}>{busy ? 'Scoring…' : `Pay ${inr(form.amount)}`}</Button>
            <span className="text-xs text-muted">Each request carries a fresh Idempotency-Key header.</span>
          </div>
          <div className="mt-3"><ErrorNote error={error} /></div>
        </Card>

        <Card title="Decision">
          {!result ? (
            <div className="flex flex-col items-center justify-center py-16 text-center">
              <Send className="h-8 w-8 text-faint" strokeWidth={1.5} />
              <div className="mt-3 text-sm font-medium text-fg">No payment scored yet</div>
              <div className="mt-1 max-w-xs text-sm text-muted">Pick a preset and press Pay. The decision, its probability and the reasons appear here.</div>
            </div>
          ) : (
            <div>
              <div className="flex items-center gap-3">
                <DecisionBadge decision={result.decision} />
                <span className="text-2xl font-semibold tabular-nums">{(result.riskScore * 100).toFixed(1)}%</span>
                <span className="text-sm text-muted">fraud probability · decided in {result.latencyMs} ms · model {result.modelVersion}</span>
              </div>
              <p className="mt-2 text-sm text-fg-2">
                {result.decision === 'APPROVE' && 'Money moves instantly. Nothing unusual enough to interrupt the customer.'}
                {result.decision === 'REVIEW' && 'Payment is held: the customer must re-confirm through a trusted channel and an analyst case is opened.'}
                {result.decision === 'BLOCK' && 'Payment is declined and an investigation case is opened.'}
              </p>
              {result.reasons.length > 0 && (
                <div className="mt-4 h-56">
                  <div className="mb-1 text-xs font-medium text-muted">What pushed the score up (log-odds contribution)</div>
                  <ResponsiveContainer>
                    <BarChart data={result.reasons} layout="vertical" margin={{ left: 40 }}>
                      <XAxis type="number" tick={CHART.tick} />
                      <YAxis type="category" dataKey="code" width={140} tick={{ ...CHART.tick, fontSize: 10 }} />
                      <Tooltip {...CHART.tooltip} formatter={(v) => Number(v).toFixed(2)} labelFormatter={(code) => result.reasons.find(r => r.code === code)?.description ?? String(code)} />
                      <Bar dataKey="contribution" radius={[0, 4, 4, 0]}>
                        {result.reasons.map(r => <Cell key={r.code} fill={r.contribution > 2 ? '#ff6363' : r.contribution > 1 ? '#f5b03d' : '#565e69'} />)}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
              <ul className="mt-2 space-y-1 text-sm text-fg-2">{result.reasons.map(r => <li key={r.code}>• {r.description}</li>)}</ul>
            </div>
          )}
        </Card>
      </div>
      {history.length > 0 && (
        <Card title="Your recent payments" className="mt-4">
          <table className="w-full text-sm"><tbody className="divide-y divide-line">
            {history.map(h => <tr key={h.txnId}><td className="py-1.5 font-mono text-xs">{h.txnId}</td><td className="font-mono text-xs">{h.payeeVpa}</td>
              <td>{inr(h.amount)}</td><td><DecisionBadge decision={h.decision} /></td><td className="text-xs">{h.riskScore.toFixed(2)}</td></tr>)}
          </tbody></table>
        </Card>
      )}
    </>
  )
}

function Field({ label, children, wide }: { label: string; children: React.ReactNode; wide?: boolean }) {
  return <label className={wide ? 'col-span-2 block' : 'block'}><span className="text-xs font-medium text-fg-2">{label}</span>{children}</label>
}
