import clsx from 'clsx'
import type { ReactNode } from 'react'
import type { Decision } from '../api'

export function Card({ title, subtitle, action, children, className, padded = true }: {
  title?: ReactNode; subtitle?: ReactNode; action?: ReactNode; children: ReactNode; className?: string; padded?: boolean
}) {
  return (
    <section className={clsx('rounded-[var(--radius-card)] border border-line bg-surface', className)}>
      {(title || action) && (
        <header className="flex flex-wrap items-start justify-between gap-3 border-b border-line px-4 py-3">
          <div className="min-w-0">
            <h2 className="text-[13px] font-semibold tracking-wide text-fg">{title}</h2>
            {subtitle && <p className="mt-0.5 text-xs text-muted">{subtitle}</p>}
          </div>
          {action}
        </header>
      )}
      <div className={clsx(padded && 'p-4')}>{children}</div>
    </section>
  )
}

const TONES = { slate: 'text-fg', green: 'text-approve', amber: 'text-review', red: 'text-block', indigo: 'text-signal' }

export function Stat({ label, value, hint, tone = 'slate' }: {
  label: string; value: ReactNode; hint?: ReactNode; tone?: keyof typeof TONES
}) {
  return (
    <div className="bg-surface px-4 py-3.5">
      <div className="font-mono text-[11px] uppercase tracking-[0.12em] text-muted">{label}</div>
      <div className={clsx('num mt-1.5 text-[26px] font-semibold leading-none tracking-tight', TONES[tone])}>{value}</div>
      {hint && <div className="mt-1.5 text-xs text-muted">{hint}</div>}
    </div>
  )
}

/** Stats sharing hairline dividers. */
export function StatGrid({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={clsx('grid gap-px overflow-hidden rounded-[var(--radius-card)] border border-line bg-line', className)}>{children}</div>
}

const DECISION_STYLE: Record<Decision, string> = {
  APPROVE: 'bg-approve/10 text-approve ring-approve/25',
  REVIEW: 'bg-review/10 text-review ring-review/25',
  BLOCK: 'bg-block/10 text-block ring-block/25',
}

export function DecisionBadge({ decision }: { decision: Decision }) {
  return (
    <span className={clsx('inline-flex items-center gap-1.5 rounded-md px-1.5 py-0.5 font-mono text-[11px] font-medium tracking-wide ring-1 ring-inset', DECISION_STYLE[decision])}>
      <span className="h-1.5 w-1.5 rounded-full bg-current" />{decision}
    </span>
  )
}

export function Badge({ children, tone = 'slate' }: { children: ReactNode; tone?: string }) {
  const tones: Record<string, string> = {
    slate: 'bg-white/[0.06] text-fg-2', indigo: 'bg-signal/12 text-signal', red: 'bg-block/12 text-block',
    amber: 'bg-review/12 text-review', green: 'bg-approve/12 text-approve',
  }
  return <span className={clsx('inline-flex items-center whitespace-nowrap rounded-md px-1.5 py-0.5 text-xs font-medium', tones[tone] ?? tones.slate)}>{children}</span>
}

export function PriorityBadge({ priority }: { priority: string }) {
  const tone = priority === 'P1' ? 'bg-block text-black' : priority === 'P2' ? 'bg-review text-black' : 'bg-white/10 text-fg-2'
  return <span className={clsx('inline-flex rounded px-1.5 py-0.5 font-mono text-[11px] font-semibold', tone)}>{priority}</span>
}

export function RiskBar({ score }: { score: number }) {
  const color = score >= 0.85 ? 'bg-block' : score >= 0.5 ? 'bg-review' : 'bg-approve'
  return (
    <div className="flex items-center gap-2">
      <div className="h-1 w-14 overflow-hidden rounded-full bg-white/10">
        <div className={clsx('h-full rounded-full', color)} style={{ width: `${Math.round(score * 100)}%` }} />
      </div>
      <span className="num font-mono text-xs text-fg-2">{score.toFixed(2)}</span>
    </div>
  )
}

export function Button({ children, onClick, variant = 'primary', disabled, type = 'button', className, size = 'md' }: {
  children: ReactNode; onClick?: () => void; variant?: 'primary' | 'secondary' | 'danger' | 'success' | 'ghost'
  disabled?: boolean; type?: 'button' | 'submit'; className?: string; size?: 'sm' | 'md' | 'lg'
}) {
  const styles = {
    primary: 'bg-signal text-[#06101f] hover:bg-[#7aaeff]',
    secondary: 'bg-raised text-fg ring-1 ring-inset ring-line-2 hover:bg-[#222831]',
    danger: 'bg-block text-[#1d0505] hover:bg-[#ff7d7d]',
    success: 'bg-approve/15 text-approve ring-1 ring-inset ring-approve/30 hover:bg-approve/25',
    ghost: 'text-fg-2 hover:bg-white/5 hover:text-fg',
  }
  const sizes = { sm: 'h-8 px-2.5 text-xs', md: 'h-9 px-3.5 text-sm', lg: 'h-11 px-4 text-sm' }
  return (
    <button type={type} onClick={onClick} disabled={disabled}
      className={clsx('focus-ring inline-flex select-none items-center justify-center gap-1.5 rounded-lg font-medium transition active:translate-y-px disabled:pointer-events-none disabled:opacity-40',
        styles[variant], sizes[size], className)}>
      {children}
    </button>
  )
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="py-10 text-center text-sm text-muted">{children}</div>
}

export function ErrorNote({ error }: { error: string | null }) {
  if (!error) return null
  return <div role="alert" className="rounded-lg border border-block/25 bg-block/[0.08] px-3 py-2 text-sm text-block">{error}</div>
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={clsx('skeleton', className)} />
}

export function ReasonChips({ reasons, max = 3 }: { reasons: { code: string; description: string }[]; max?: number }) {
  return (
    <div className="flex flex-wrap gap-1">
      {reasons.slice(0, max).map(r => (
        <span key={r.code} title={r.description} className="rounded border border-line-2 px-1.5 py-px font-mono text-[10px] text-fg-2">{r.code}</span>
      ))}
    </div>
  )
}

/** FraudShield mark: a shield cut by a single scan line. */
export function Logo({ className, compact }: { className?: string; compact?: boolean }) {
  return (
    <span className={clsx('inline-flex items-center gap-2.5', className)}>
      <svg viewBox="0 0 32 32" className="h-7 w-7" aria-hidden>
        <path d="M16 3 27 7v8.5c0 7-4.7 11.6-11 13.5C9.7 27.1 5 22.5 5 15.5V7l11-4Z" fill="#5b9bff" />
        <path d="M9 16h4.5l2-4 3 8 2-4H23" fill="none" stroke="#0a0c0f" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      {!compact && <span className="text-[16px] font-semibold tracking-tight text-fg">FraudShield</span>}
    </span>
  )
}

/** Shared chart styling for the dark theme. */
export const CHART = {
  grid: '#1f252d', tick: { fontSize: 11, fill: '#7d8590' },
  tooltip: { contentStyle: { background: '#161a1f', border: '1px solid #2d343e', borderRadius: 8, fontSize: 12, color: '#e8eaed' }, itemStyle: { color: '#e8eaed' }, labelStyle: { color: '#b4bac3' } },
}
