import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
import { useEffect, useRef, useState } from 'react'

type Decision = 'APPROVE' | 'REVIEW' | 'BLOCK'
interface Row { id: number; from: string; to: string; amount: number; score: number; decision: Decision; reason: string; ms: number }

// A believable mix: mostly everyday payments, then the occasional attack.
const SAMPLES: Omit<Row, 'id'>[] = [
  { from: 'riya.iyer834@okhdfcbank', to: 'zepto@axisbank', amount: 166, score: 0.01, decision: 'APPROVE', reason: 'usual merchant', ms: 3 },
  { from: 'arjun.k@oksbi', to: 'mahanagargas@icici', amount: 512, score: 0.02, decision: 'APPROVE', reason: 'usual amount', ms: 4 },
  { from: 'divya.pillai17@okicici', to: 'fastpay83130@ybl', amount: 74000, score: 0.97, decision: 'BLOCK', reason: 'new device · 1,750 km away', ms: 6 },
  { from: 'neha.iyer498@okaxis', to: 'dmart.thane@icici', amount: 2140, score: 0.04, decision: 'APPROVE', reason: 'known payee', ms: 4 },
  { from: 'sunita.rao@okaxis', to: 'cyber.cell.verify@okaxis', amount: 95000, score: 0.71, decision: 'REVIEW', reason: '186× usual · new payee', ms: 5 },
  { from: 'manoj.mehta199@okaxis', to: 'chaiwala.kumar@paytm', amount: 30, score: 0.00, decision: 'APPROVE', reason: 'trusted history', ms: 3 },
  { from: 'job.offer.hr@ybl', to: 'agent2eef@axl', amount: 17776, score: 0.91, decision: 'BLOCK', reason: 'pass-through · mule watch-list', ms: 5 },
  { from: 'kiran.v@okhdfcbank', to: 'swiggy@icici', amount: 389, score: 0.03, decision: 'APPROVE', reason: 'usual merchant', ms: 4 },
]

const COLOR: Record<Decision, string> = { APPROVE: '#3ecf8e', REVIEW: '#f5b03d', BLOCK: '#ff6363' }
const inr = (v: number) => '₹' + v.toLocaleString('en-IN')

export default function ScoringFeed({ rows = 6 }: { rows?: number }) {
  const reduce = useReducedMotion()
  const n = useRef(rows)
  const [feed, setFeed] = useState<Row[]>(() => SAMPLES.slice(0, rows).map((s, i) => ({ ...s, id: rows - i })))
  const [count, setCount] = useState(184_302)

  useEffect(() => {
    if (reduce) return
    const t = setInterval(() => {
      const id = ++n.current
      setFeed(f => [{ ...SAMPLES[id % SAMPLES.length], id }, ...f].slice(0, rows))
      setCount(c => c + 1)
    }, 1300)
    return () => clearInterval(t)
  }, [reduce, rows])

  return (
    <div className="overflow-hidden rounded-xl border border-line bg-surface/90 font-mono text-[12px] shadow-[0_30px_80px_-30px_rgb(0_0_0/0.8)] backdrop-blur">
      <div className="flex items-center justify-between border-b border-line px-4 py-2.5 text-[11px]">
        <span className="flex items-center gap-2 text-fg-2"><span className="h-1.5 w-1.5 animate-pulse rounded-full bg-approve" /> payments.scored</span>
        <span className="num text-faint">{count.toLocaleString('en-IN')} decisions · p95 9 ms</span>
      </div>
      <div style={{ height: rows * 58 }} className="relative">
        <AnimatePresence initial={false} mode="popLayout">
          {feed.map(r => (
            <motion.div key={r.id} layout initial={{ opacity: 0, y: -18 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
              transition={{ type: 'spring', stiffness: 420, damping: 36 }}
              className="grid h-[58px] grid-cols-[1fr_auto] items-center gap-3 border-b border-line/70 px-4">
              <div className="min-w-0">
                <div className="truncate text-fg-2">{r.from} <span className="text-faint">→</span> {r.to}</div>
                <div className="mt-1 flex items-center gap-2 text-[11px] text-faint">
                  <span className="num text-fg">{inr(r.amount)}</span>
                  <span className="h-1 w-16 overflow-hidden rounded-full bg-white/10">
                    <motion.span className="block h-full rounded-full" style={{ background: COLOR[r.decision] }}
                      initial={{ width: 0 }} animate={{ width: `${Math.max(4, r.score * 100)}%` }} transition={{ duration: 0.5, delay: 0.1 }} />
                  </span>
                  <span className="truncate">{r.reason}</span>
                </div>
              </div>
              <div className="text-right">
                <motion.span initial={{ scale: 0.6, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} transition={{ delay: 0.35, type: 'spring', stiffness: 500, damping: 22 }}
                  className="inline-flex rounded px-1.5 py-0.5 text-[10px] font-medium ring-1 ring-inset"
                  style={{ color: COLOR[r.decision], background: COLOR[r.decision] + '1a', boxShadow: `inset 0 0 0 1px ${COLOR[r.decision]}40` }}>{r.decision}</motion.span>
                <div className="num mt-1 text-[10px] text-faint">{r.ms} ms</div>
              </div>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </div>
  )
}
