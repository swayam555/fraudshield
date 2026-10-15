import { useState, type FormEvent } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { motion } from 'motion/react'
import { ArrowLeft, ArrowRight } from 'lucide-react'
import { useAuth } from '../auth'
import { Button, ErrorNote, Logo } from '../components/ui'
import ScoringFeed from '../landing/ScoringFeed'

const ACCOUNTS = [{ u: 'analyst', name: 'Priya Raman', role: 'Fraud analyst' }, { u: 'lead', name: 'Arjun Mehta', role: 'Fraud team lead' }]

export default function Login() {
  const { user, login } = useAuth()
  const [username, setUsername] = useState('analyst')
  const [password, setPassword] = useState('fraudshield')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  if (user) return <Navigate to="/app" replace />

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true); setError(null)
    try { await login(username, password) }
    catch (err) { setError((err as Error).message) }
    finally { setBusy(false) }
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-[1.1fr_1fr]">
      <aside className="relative hidden flex-col justify-between overflow-hidden border-r border-line bg-[#0d1013] p-12 lg:flex">
        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_30%_20%,rgb(91_155_255/0.10),transparent_55%)]" />
        <Link to="/" className="relative w-fit"><Logo /></Link>
        <div className="relative">
          <div className="font-mono text-[11px] uppercase tracking-[0.16em] text-signal">Live decisions</div>
          <h2 className="mt-3 max-w-md text-[34px] font-semibold leading-tight tracking-tight text-fg">Every payment, scored before the money moves.</h2>
          <div className="mt-8 max-w-lg"><ScoringFeed rows={5} /></div>
        </div>
        <p className="relative text-xs text-faint">Demo system. All customers and payments are synthetic.</p>
      </aside>
      <main className="flex flex-col px-6 py-8 sm:px-12">
        <Link to="/" className="inline-flex w-fit items-center gap-1.5 text-sm text-muted hover:text-fg"><ArrowLeft className="h-4 w-4" /> Back to overview</Link>
        <motion.form onSubmit={submit} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="m-auto w-full max-w-[360px] py-12">
          <div className="lg:hidden"><Logo /></div>
          <h1 className="mt-8 text-[26px] font-semibold tracking-tight text-fg lg:mt-0">Analyst sign in</h1>
          <p className="mt-1.5 text-sm text-muted">Access the live monitor, cases and the copilot.</p>
          <div className="mt-8 space-y-4">
            <label className="block text-[13px] font-medium text-fg-2">Username<input className="input" value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" /></label>
            <label className="block text-[13px] font-medium text-fg-2">Password<input className="input" type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" /></label>
            <ErrorNote error={error} />
            <Button type="submit" size="lg" disabled={busy} className="w-full">{busy ? 'Signing in…' : <>Sign in <ArrowRight className="h-4 w-4" /></>}</Button>
          </div>
          <div className="mt-10">
            <div className="font-mono text-[10px] uppercase tracking-[0.16em] text-faint">Demo accounts · password fraudshield</div>
            <div className="mt-3 grid gap-2">
              {ACCOUNTS.map(a => (
                <button type="button" key={a.u} onClick={() => { setUsername(a.u); setPassword('fraudshield') }}
                  className={`focus-ring flex items-center justify-between rounded-lg border px-3.5 py-2.5 text-left transition ${username === a.u ? 'border-signal/60 bg-signal/[0.06]' : 'border-line hover:border-line-2'}`}>
                  <span><span className="block text-sm text-fg">{a.name}</span><span className="block text-xs text-muted">{a.role}</span></span>
                  <span className="font-mono text-xs text-muted">{a.u}</span>
                </button>
              ))}
            </div>
          </div>
        </motion.form>
      </main>
    </div>
  )
}
