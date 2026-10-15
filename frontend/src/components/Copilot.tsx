import { useState } from 'react'
import ReactMarkdown from 'react-markdown'
import { Bot, FileText, Send, Sparkles } from 'lucide-react'
import { post, type CopilotAnswer } from '../api'
import { Badge, Button, ErrorNote } from './ui'

export function AnswerView({ answer }: { answer: CopilotAnswer }) {
  return (
    <div>
      <div className="prose-brief"><ReactMarkdown>{answer.answer}</ReactMarkdown></div>
      <div className="mt-2 flex items-center gap-2 text-[11px] text-muted">
        <Badge tone={answer.llm ? 'indigo' : 'slate'}>{answer.generatedBy}</Badge>
        <span>{answer.latencyMs} ms</span>
      </div>
      {answer.citations.length > 0 && (
        <details className="mt-3 rounded-lg border border-line bg-surface-2 p-2" open>
          <summary className="cursor-pointer text-xs font-medium text-fg-2">Sources ({answer.citations.length}) · hybrid retrieval</summary>
          <ol className="mt-2 space-y-2">
            {answer.citations.map(c => (
              <li key={c.n} className="text-xs">
                <div className="flex items-start gap-1.5">
                  <span className="font-semibold text-signal">[{c.n}]</span>
                  <div className="min-w-0">
                    <div className="font-medium text-fg"><FileText className="mr-1 inline h-3 w-3" />{c.docTitle} - {c.section}</div>
                    <div className="mt-0.5 line-clamp-2 text-muted">{c.snippet}</div>
                    <div className="mt-0.5 text-[10px] text-faint">
                      semantic rank {c.denseRank ?? '-'} · keyword rank {c.lexicalRank ?? '-'} · RRF {c.score.toFixed(4)}
                    </div>
                  </div>
                </div>
              </li>
            ))}
          </ol>
        </details>
      )}
    </div>
  )
}

export default function Copilot({ caseId, initialSummary, initialBy }: {
  caseId: number; initialSummary: string | null; initialBy: string | null
}) {
  const [summary, setSummary] = useState<CopilotAnswer | null>(
    initialSummary ? { answer: initialSummary, citations: [], generatedBy: initialBy ?? '', llm: !(initialBy ?? '').includes('fallback'), latencyMs: 0 } : null)
  const [question, setQuestion] = useState('')
  const [thread, setThread] = useState<{ q: string; a: CopilotAnswer }[]>([])
  const [busy, setBusy] = useState<'summary' | 'ask' | null>(null)
  const [error, setError] = useState<string | null>(null)

  const brief = async () => {
    setBusy('summary'); setError(null)
    try { setSummary(await post<CopilotAnswer>(`/api/copilot/cases/${caseId}/summary`)) }
    catch (e) { setError((e as Error).message) } finally { setBusy(null) }
  }
  const ask = async (q: string) => {
    if (!q.trim()) return
    setBusy('ask'); setError(null)
    try {
      const a = await post<CopilotAnswer>('/api/copilot/ask', { question: q, caseId })
      setThread(t => [...t, { q, a }]); setQuestion('')
    } catch (e) { setError((e as Error).message) } finally { setBusy(null) }
  }

  const suggestions = ['Should we file an STR for this case, and by when?', 'Is the customer liable for this loss?',
    'What should I say when I call the customer?']

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2 text-sm font-semibold text-fg"><Bot className="h-4 w-4 text-signal" /> AI investigator copilot</div>
        <Button onClick={brief} disabled={busy !== null}><Sparkles className="h-4 w-4" />{busy === 'summary' ? 'Writing brief…' : summary ? 'Regenerate brief' : 'Generate brief'}</Button>
      </div>
      <ErrorNote error={error} />
      {summary ? <AnswerView answer={summary} /> : (
        <p className="text-sm text-muted">Generate an investigation brief: the copilot reads the case facts and live account
          intelligence, retrieves the relevant RBI/FIU-IND guidance and playbooks, and writes a cited summary.</p>
      )}
      <div className="border-t border-line pt-3">
        {thread.map((t, i) => (
          <div key={i} className="mb-4">
            <div className="mb-1 text-sm font-medium text-fg">Q: {t.q}</div>
            <AnswerView answer={t.a} />
          </div>
        ))}
        <div className="mb-2 flex flex-wrap gap-1.5">
          {suggestions.map(s => (
            <button key={s} onClick={() => ask(s)} disabled={busy !== null}
              className="rounded-full border border-line px-2.5 py-1 text-xs text-fg-2 hover:border-signal/50 hover:text-signal">{s}</button>
          ))}
        </div>
        <form onSubmit={e => { e.preventDefault(); ask(question) }} className="flex gap-2">
          <input value={question} onChange={e => setQuestion(e.target.value)} placeholder="Ask about this case or policy…"
            className="flex-1 rounded-lg border border-line-2 px-3 py-1.5 text-sm" />
          <Button type="submit" disabled={busy !== null}><Send className="h-4 w-4" />{busy === 'ask' ? 'Thinking…' : 'Ask'}</Button>
        </form>
      </div>
    </div>
  )
}
