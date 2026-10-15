import { useEffect, useState } from 'react'
import { Search } from 'lucide-react'
import { PageHeader } from '../components/Layout'
import { Badge, Button, Card, ErrorNote } from '../components/ui'
import { AnswerView } from '../components/Copilot'
import { api, post, type CopilotAnswer } from '../api'

interface Passage { id: number; docTitle: string; section: string; content: string; denseRank: number | null; lexicalRank: number | null; rrfScore: number }
interface Status { llmEnabled: boolean; chatModel: string; embeddingModel: string; retrieval: string; knowledgeChunks: number }
interface Doc { doc_id: string; doc_title: string; source: string; chunks: number }

export default function Knowledge() {
  const [q, setQ] = useState('When do we have to file a suspicious transaction report?')
  const [hits, setHits] = useState<Passage[]>([])
  const [answer, setAnswer] = useState<CopilotAnswer | null>(null)
  const [status, setStatus] = useState<Status | null>(null)
  const [docs, setDocs] = useState<Doc[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api<Status>('/api/copilot/status').then(setStatus).catch(() => {})
    api<Doc[]>('/api/kb/documents').then(setDocs).catch(() => {})
  }, [])

  const run = async () => {
    setBusy(true); setError(null)
    try {
      const [h, a] = await Promise.all([
        api<Passage[]>(`/api/kb/search?q=${encodeURIComponent(q)}&k=6`),
        post<CopilotAnswer>('/api/copilot/ask', { question: q }),
      ])
      setHits(h); setAnswer(a)
    } catch (e) { setError((e as Error).message) } finally { setBusy(false) }
  }

  return (
    <>
      <PageHeader title="Knowledge base"
        subtitle="Retrieval-augmented generation over RBI, FIU-IND and NPCI guidance plus internal playbooks. Semantic search (pgvector) and keyword search (Postgres full-text) are fused with Reciprocal Rank Fusion, so both paraphrases and exact terms like 'STR' or '1930' are found." />
      {status && (
        <div className="mb-4 flex flex-wrap gap-2 text-xs">
          <Badge tone={status.llmEnabled ? 'green' : 'amber'}>Chat model: {status.chatModel === 'none' ? 'not configured (rule-based fallback)' : status.chatModel}</Badge>
          <Badge tone="indigo">Embeddings: {status.embeddingModel}</Badge>
          <Badge>{status.knowledgeChunks} chunks indexed</Badge>
        </div>
      )}
      <Card>
        <form onSubmit={e => { e.preventDefault(); run() }} className="flex gap-2">
          <input value={q} onChange={e => setQ(e.target.value)} className="flex-1 rounded-lg border border-line-2 px-3 py-2 text-sm" />
          <Button type="submit" disabled={busy}><Search className="h-4 w-4" />{busy ? 'Searching…' : 'Ask'}</Button>
        </form>
        <div className="mt-2 flex flex-wrap gap-1.5">
          {['Is a customer liable if they shared their UPI PIN?', 'someone recruited to receive stolen money and send it on',
            'What is a digital arrest scam?', 'new device cooling period limit'].map(s => (
            <button key={s} onClick={() => setQ(s)} className="rounded-full border border-line px-2.5 py-1 text-xs text-fg-2 hover:border-signal/50">{s}</button>
          ))}
        </div>
      </Card>
      <ErrorNote error={error} />
      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-2">
        <Card title="Copilot answer">{answer ? <AnswerView answer={answer} /> : <p className="text-sm text-muted">Ask a question.</p>}</Card>
        <Card title="Retrieved passages: how each retriever ranked them">
          {hits.length === 0 ? <p className="text-sm text-muted">Results appear here.</p> : (
            <ol className="space-y-3">{hits.map((h, i) => (
              <li key={h.id} className="text-sm">
                <div className="flex items-center justify-between gap-2">
                  <div className="font-medium text-fg">{i + 1}. {h.docTitle} - {h.section}</div>
                  <div className="flex shrink-0 gap-1 text-[10px]">
                    <Badge tone={h.denseRank ? 'indigo' : 'slate'}>semantic #{h.denseRank ?? '-'}</Badge>
                    <Badge tone={h.lexicalRank ? 'green' : 'slate'}>keyword #{h.lexicalRank ?? '-'}</Badge>
                    <Badge>RRF {h.rrfScore.toFixed(4)}</Badge>
                  </div>
                </div>
                <p className="mt-1 line-clamp-3 text-xs text-fg-2">{h.content}</p>
              </li>))}</ol>
          )}
        </Card>
      </div>
      <Card title="Indexed documents" className="mt-4">
        <table className="w-full text-sm"><tbody className="divide-y divide-line">
          {docs.map(d => <tr key={d.doc_id}><td className="py-1.5 font-medium">{d.doc_title}</td><td className="text-xs text-muted">{d.source}</td><td className="text-right text-xs">{d.chunks} chunks</td></tr>)}
        </tbody></table>
      </Card>
    </>
  )
}
