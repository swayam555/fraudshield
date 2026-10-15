import { useMemo, useState } from 'react'
import { forceCenter, forceCollide, forceLink, forceManyBody, forceSimulation, type SimulationNodeDatum } from 'd3-force'
import { compactInr, type Graph } from '../api'

interface N extends SimulationNodeDatum { id: string; level: number; muleScore: number; denied: boolean }

const W = 680
const H = 400

/** Payment network around an account, laid out once with a d3-force simulation and drawn as SVG. */
export default function GraphView({ graph, onSelect }: { graph: Graph; onSelect?: (vpa: string) => void }) {
  const [hover, setHover] = useState<string | null>(null)
  const layout = useMemo(() => {
    const nodes: N[] = graph.nodes.map(n => ({ ...n }))
    const links = graph.edges.map(e => ({ source: e.from, target: e.to, amount: e.amount, count: e.count }))
    const sim = forceSimulation(nodes)
      .force('link', forceLink<N, (typeof links)[number]>(links).id(d => d.id).distance(95))
      .force('charge', forceManyBody().strength(-260))
      .force('center', forceCenter(W / 2, H / 2))
      .force('collide', forceCollide(30))
      .stop()
    for (let i = 0; i < 300; i++) sim.tick()
    // Scale the finished layout into the viewport instead of clamping, so nothing piles up at the edges.
    const xs = nodes.map(n => n.x ?? 0), ys = nodes.map(n => n.y ?? 0)
    const [minX, maxX, minY, maxY] = [Math.min(...xs), Math.max(...xs), Math.min(...ys), Math.max(...ys)]
    const pad = { x: 60, y: 34 }
    const sx = (W - 2 * pad.x) / Math.max(1, maxX - minX), sy = (H - 2 * pad.y) / Math.max(1, maxY - minY)
    const k = Math.min(sx, sy, 1.6)
    const ox = (W - (maxX - minX) * k) / 2, oy = (H - (maxY - minY) * k) / 2
    nodes.forEach(n => { n.x = ox + ((n.x ?? 0) - minX) * k; n.y = oy + ((n.y ?? 0) - minY) * k })
    return { nodes, links: links as unknown as { source: N; target: N; amount: number; count: number }[] }
  }, [graph])

  const maxAmount = Math.max(1, ...layout.links.map(l => l.amount))
  const color = (n: N) => n.denied ? '#b42318' : n.muleScore >= 0.7 ? '#ff6363' : n.muleScore >= 0.3 ? '#f5b03d' : n.id === graph.root ? '#5b9bff' : '#4a525d'
  // Label only what an investigator needs at a glance; everything else on hover.
  const labelled = (n: N) => n.id === graph.root || n.muleScore >= 0.3 || n.denied || n.id === hover
  const short = (id: string) => (id.length > 18 ? id.slice(0, 16) + '…' : id)
  const touches = (l: { source: N; target: N }) => hover && (l.source.id === hover || l.target.id === hover)

  return (
    <div>
      <svg viewBox={`0 0 ${W} ${H}`} className="w-full rounded-lg bg-[#0d1013]" onMouseLeave={() => setHover(null)}>
        <defs>
          <pattern id="dots" width="20" height="20" patternUnits="userSpaceOnUse"><circle cx="1" cy="1" r="1" fill="#1b2027" /></pattern>
          <marker id="arrow" viewBox="0 0 10 10" refX="24" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#3a424d" />
          </marker>
        </defs>
        <rect width={W} height={H} fill="url(#dots)" />
        {layout.links.map((l, i) => (
          <line key={i} x1={l.source.x} y1={l.source.y} x2={l.target.x} y2={l.target.y}
            stroke={touches(l) ? '#5b9bff' : '#2d343e'} strokeOpacity={hover && !touches(l) ? 0.35 : 1}
            strokeWidth={1 + 4 * (l.amount / maxAmount)} markerEnd="url(#arrow)">
            <title>{`${l.source.id} → ${l.target.id}: ${compactInr(l.amount)} in ${l.count} payment(s)`}</title>
          </line>
        ))}
        {layout.nodes.map(n => {
          const r = n.id === graph.root ? 12 : n.muleScore >= 0.3 ? 9 : 7
          return (
            <g key={n.id} transform={`translate(${n.x},${n.y})`} className="cursor-pointer"
              onClick={() => onSelect?.(n.id)} onMouseEnter={() => setHover(n.id)}>
              {n.id === graph.root && <circle r={20} fill="#5b9bff" opacity={0.12} />}
              {n.muleScore >= 0.7 && <circle r={15} fill="#ff6363" opacity={0.14} />}
              <circle r={r} fill={color(n)} stroke="#0d1013" strokeWidth={2} />
              {labelled(n) && (
                <text y={r + 13} textAnchor="middle" fontSize={10} fontFamily="IBM Plex Mono, monospace"
                  fill={n.id === graph.root ? '#e8eaed' : '#9aa1ab'} paintOrder="stroke" stroke="#0d1013" strokeWidth={3}>
                  {short(n.id)}
                </text>
              )}
              <title>{`${n.id}\nmule score ${n.muleScore.toFixed(2)}${n.denied ? '\nDENY-LISTED' : ''}`}</title>
            </g>
          )
        })}
      </svg>
      <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1.5 text-[11px] text-muted">
        <Legend c="#5b9bff" l="Case subject" /><Legend c="#f5b03d" l="Watch-list (mule score ≥ 0.3)" />
        <Legend c="#ff6363" l="Likely mule (≥ 0.7)" /><Legend c="#b42318" l="Deny-listed" /><Legend c="#4a525d" l="Other account" />
        <span>· edge width = amount moved · hover to trace</span>
      </div>
    </div>
  )
}

function Legend({ c, l }: { c: string; l: string }) {
  return <span className="flex items-center gap-1.5"><span className="inline-block h-2 w-2 rounded-full" style={{ background: c }} />{l}</span>
}
