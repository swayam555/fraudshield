import { useReducedMotion } from 'motion/react'

interface Node { id: string; x: number; y: number; w: number; title: string; sub: string; tone?: 'ext' | 'bus' | 'store' }
const H = 58
const NODES: Node[] = [
  { id: 'ui', x: 10, y: 150, w: 130, title: 'Console', sub: 'React · SSE', tone: 'ext' },
  { id: 'gw', x: 175, y: 150, w: 125, title: 'Gateway', sub: 'JWT · rate limits' },
  { id: 'txn', x: 340, y: 40, w: 140, title: 'txn-service', sub: 'idempotency · outbox' },
  { id: 'risk', x: 340, y: 150, w: 140, title: 'risk-engine', sub: 'Lua features · model' },
  { id: 'case', x: 340, y: 260, w: 140, title: 'case-service', sub: 'cases · RAG copilot' },
  { id: 'kafka', x: 530, y: 150, w: 115, title: 'Kafka', sub: '3 topics', tone: 'bus' },
  { id: 'redis', x: 690, y: 80, w: 120, title: 'Redis', sub: 'features · graph', tone: 'store' },
  { id: 'pg', x: 690, y: 220, w: 120, title: 'Postgres', sub: 'pgvector · cases', tone: 'store' },
]
const n = (id: string) => NODES.find(x => x.id === id)!
const R = (id: string) => [n(id).x + n(id).w, n(id).y + H / 2] as const
const L = (id: string) => [n(id).x, n(id).y + H / 2] as const
const curve = (a: readonly [number, number], b: readonly [number, number]) => {
  const mx = (a[0] + b[0]) / 2
  return `M ${a[0]} ${a[1]} C ${mx} ${a[1]}, ${mx} ${b[1]}, ${b[0]} ${b[1]}`
}
const EDGES = [
  curve(R('ui'), L('gw')), curve(R('gw'), L('txn')), curve(R('gw'), L('risk')), curve(R('gw'), L('case')),
  `M ${n('txn').x + 70} ${n('txn').y + H} L ${n('risk').x + 70} ${n('risk').y}`,
  curve(R('txn'), L('kafka')), curve(R('risk'), L('kafka')), curve(R('case'), L('kafka')),
  curve(R('kafka'), L('redis')), curve(R('kafka'), L('pg')),
]

export default function Architecture() {
  const reduce = useReducedMotion()
  return (
    <div className="overflow-x-auto">
      <svg viewBox="0 0 820 340" className="mx-auto min-w-[720px] max-w-full" role="img"
        aria-label="FraudShield architecture: the console calls the gateway, which routes to txn-service, risk-engine and case-service; txn-service scores synchronously with the risk engine; all three exchange events over Kafka and keep state in Redis and Postgres.">
        {EDGES.map((d, i) => (
          <g key={i}>
            <path d={d} fill="none" stroke="#2d343e" strokeWidth="1.5" />
            {!reduce && <circle r="3" fill="#5b9bff"><animateMotion dur={`${2.2 + (i % 4) * 0.4}s`} begin={`${i * 0.25}s`} repeatCount="indefinite" path={d} /></circle>}
          </g>
        ))}
        {NODES.map(x => (
          <g key={x.id} transform={`translate(${x.x} ${x.y})`}>
            <rect width={x.w} height={H} rx="10" fill={x.tone === 'bus' ? '#5b9bff' : x.tone === 'store' ? '#161a1f' : '#111418'}
              stroke={x.tone === 'bus' ? '#5b9bff' : '#2d343e'} strokeDasharray={x.tone === 'ext' ? '4 4' : undefined} />
            <text x="13" y="25" fontSize="13" fontWeight="600" fill={x.tone === 'bus' ? '#06101f' : '#e8eaed'}>{x.title}</text>
            <text x="13" y="43" fontSize="10.5" fontFamily="IBM Plex Mono, monospace" fill={x.tone === 'bus' ? '#06101fcc' : '#7d8590'}>{x.sub}</text>
          </g>
        ))}
      </svg>
    </div>
  )
}
