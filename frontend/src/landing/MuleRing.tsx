import { motion, useInView, useReducedMotion } from 'motion/react'
import { useEffect, useRef, useState } from 'react'

// Victims pay three new "mule" accounts; the mules forward everything to one collector within minutes.
const VICTIMS = Array.from({ length: 9 }, (_, i) => ({ id: `v${i}`, x: 70, y: 40 + i * 38 }))
const MULES = [{ id: 'm0', x: 300, y: 92 }, { id: 'm1', x: 300, y: 194 }, { id: 'm2', x: 300, y: 296 }]
const COLLECTOR = { x: 520, y: 194 }
const victimMule = (i: number) => MULES[Math.floor(i / 3)]

/** One payment at a time looks harmless; the shape of the network does not. */
export default function MuleRing() {
  const ref = useRef<HTMLDivElement>(null)
  const inView = useInView(ref, { once: true, margin: '-120px' })
  const reduce = useReducedMotion()
  const [stage, setStage] = useState(reduce ? 3 : 0)

  useEffect(() => {
    if (!inView || reduce) return
    const t = [setTimeout(() => setStage(1), 300), setTimeout(() => setStage(2), 2400), setTimeout(() => setStage(3), 3800)]
    return () => t.forEach(clearTimeout)
  }, [inView, reduce])

  return (
    <div ref={ref} className="rounded-xl border border-line bg-[#0d1013] p-4">
      <svg viewBox="0 0 600 380" className="w-full">
        {VICTIMS.map((v, i) => {
          const m = victimMule(i)
          return (
            <motion.line key={v.id} x1={v.x} y1={v.y} x2={m.x} y2={m.y} stroke="#3a424d" strokeWidth={1.5}
              initial={{ pathLength: 0, opacity: 0 }} animate={stage >= 1 ? { pathLength: 1, opacity: 1 } : {}} transition={{ duration: 0.5, delay: i * 0.18 }} />
          )
        })}
        {MULES.map((m, i) => (
          <motion.line key={m.id} x1={m.x} y1={m.y} x2={COLLECTOR.x} y2={COLLECTOR.y} stroke={stage >= 3 ? '#ff6363' : '#3a424d'} strokeWidth={3}
            initial={{ pathLength: 0, opacity: 0 }} animate={stage >= 2 ? { pathLength: 1, opacity: 1 } : {}} transition={{ duration: 0.6, delay: i * 0.15 }} />
        ))}
        {VICTIMS.map(v => (
          <g key={v.id}>
            <circle cx={v.x} cy={v.y} r={8} fill="#4a525d" />
          </g>
        ))}
        <text x={70} y={372} textAnchor="middle" fill="#7d8590" fontSize={11} fontFamily="IBM Plex Mono, monospace">victims</text>
        {MULES.map((m, i) => (
          <g key={m.id}>
            {stage >= 3 && <motion.circle cx={m.x} cy={m.y} r={22} fill="#ff6363" initial={{ opacity: 0, scale: 0.5 }} animate={{ opacity: 0.15, scale: 1 }} />}
            <motion.circle cx={m.x} cy={m.y} r={12} animate={{ fill: stage >= 3 ? '#ff6363' : stage >= 1 ? '#f5b03d' : '#4a525d' }} transition={{ delay: i * 0.1 }} />
            <text x={m.x} y={m.y + 32} textAnchor="middle" fill="#9aa1ab" fontSize={11} fontFamily="IBM Plex Mono, monospace">{`agent${i + 1}@axl`}</text>
          </g>
        ))}
        <motion.circle cx={COLLECTOR.x} cy={COLLECTOR.y} r={15} animate={{ fill: stage >= 3 ? '#b42318' : '#4a525d' }} />
        <text x={COLLECTOR.x} y={COLLECTOR.y + 36} textAnchor="middle" fill="#9aa1ab" fontSize={11} fontFamily="IBM Plex Mono, monospace">collector</text>
      </svg>
      <div className="mt-2 grid grid-cols-3 gap-2 border-t border-line pt-3 text-xs">
        {[['Fan-in', 'many strangers pay one new account'], ['Pass-through', 'money leaves within minutes'], ['Watch-list', 'the next payment in is held']].map(([t, d], i) => (
          <motion.div key={t} animate={{ opacity: stage > i ? 1 : 0.35 }}>
            <div className="font-medium text-fg">{t}</div><div className="mt-0.5 text-muted">{d}</div>
          </motion.div>
        ))}
      </div>
    </div>
  )
}
