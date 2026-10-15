export type Decision = 'APPROVE' | 'REVIEW' | 'BLOCK'

export interface Reason { code: string; description: string; contribution: number }

export interface PaymentResult {
  txnId: string; payerVpa: string; payeeVpa: string; amount: number; decision: Decision; riskScore: number
  reasons: Reason[]; modelVersion: string; latencyMs: number; degraded: boolean; createdAt: string
}

export interface LivePayment {
  txnId: string; payer: string; payee: string; amount: number; decision: Decision; riskScore: number
  reasons: Reason[]; latencyMs: number; degraded: boolean; simLabel: string | null; at: string
}

export interface LiveStats {
  tps: number; approved: number; reviewed: number; blocked: number; blockedAmount: number; reviewedAmount: number
  p50LatencyMs: number; p95LatencyMs: number; p99LatencyMs: number; degraded: number; totalPayments: number
  totalBlockedAmount: number
  series: { t: number; APPROVE: number; REVIEW: number; BLOCK: number }[]
  groundTruth: { fraudCaught: number; fraudMissed: number; legitFlagged: number; legitPassed: number
    detectionRate: number; falsePositiveRate: number }
}

export interface CaseRow {
  id: number; subjectVpa: string; caseType: string; typology: string; status: string; priority: string
  riskScore: number; totalAmount: number; paymentCount: number; assignee: string | null; groundTruth: string | null
  aiSummary: string | null; aiSummaryBy: string | null; aiSummaryAt: string | null; resolution: string | null
  createdAt: string; updatedAt: string; slaDueAt: string
}

export interface CasePayment {
  txnId: string; payerVpa: string; payeeVpa: string; amount: number; decision: Decision; riskScore: number
  reasons: Reason[]; deviceId: string; simLabel: string | null; paidAt: string
}

export interface CaseDetail {
  kase: CaseRow; typologyLabel: string; payments: CasePayment[]
  timeline: { id: number; at: string; actor: string; eventType: string; message: string }[]
}

export interface Citation {
  n: number; docId: string; docTitle: string; section: string; snippet: string; source: string | null
  denseRank: number | null; lexicalRank: number | null; score: number
}

export interface CopilotAnswer { answer: string; citations: Citation[]; generatedBy: string; llm: boolean; latencyMs: number }

export interface Graph {
  root: string
  nodes: { id: string; level: number; muleScore: number; denied: boolean }[]
  edges: { from: string; to: string; amount: number; count: number }[]
}

export interface SimStatus {
  running: boolean; warmingUp: boolean; warmedUp: boolean; tps: number; fraudRate: number; sent: number; errors: number
  pendingAttackSteps: number; scoreboard: Record<string, Record<Decision, number>>
}

const TOKEN_KEY = 'fraudshield.token'

export const auth = {
  get token() { return localStorage.getItem(TOKEN_KEY) },
  set(token: string) { localStorage.setItem(TOKEN_KEY, token) },
  clear() { localStorage.removeItem(TOKEN_KEY) },
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) { super(message); this.status = status }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (auth.token) headers.set('Authorization', `Bearer ${auth.token}`)
  const res = await fetch(path, { ...init, headers })
  if (res.status === 401 && !path.startsWith('/api/auth')) {
    auth.clear()
    window.location.href = '/login'
  }
  if (!res.ok) {
    let message = res.statusText
    try {
      const body = await res.json()
      message = body.detail ?? body.error ?? message
    } catch { /* not json */ }
    if (res.status === 429) message = 'Rate limit reached - slow down a little.'
    throw new ApiError(res.status, message)
  }
  return res.status === 204 ? (undefined as T) : res.json()
}

export const post = <T,>(path: string, body?: unknown, headers?: Record<string, string>) =>
  api<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body), headers })

export const inr = (v: number, digits = 0) =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: digits }).format(v)

export const compactInr = (v: number) => {
  if (v >= 1e7) return `₹${(v / 1e7).toFixed(2)} Cr`
  if (v >= 1e5) return `₹${(v / 1e5).toFixed(2)} L`
  return inr(v)
}

export const timeAgo = (iso: string) => {
  const s = Math.round((Date.now() - new Date(iso).getTime()) / 1000)
  if (s < 60) return `${Math.max(s, 0)}s ago`
  if (s < 3600) return `${Math.floor(s / 60)}m ago`
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`
  return `${Math.floor(s / 86400)}d ago`
}

export const TYPOLOGY_LABEL: Record<string, string> = {
  MONEY_MULE: 'Money mule',
  ACCOUNT_TAKEOVER: 'Account takeover',
  ACCOUNT_PROBING: 'UPI ID probing',
  SOCIAL_ENGINEERING_SCAM: 'Social-engineering scam',
  SUSPICIOUS_ACTIVITY: 'Suspicious activity',
}

export const SCENARIO_LABEL: Record<string, string> = {
  LEGIT: 'Genuine customers',
  MULE_RING: 'Mule ring',
  ACCOUNT_TAKEOVER: 'Account takeover',
  DIGITAL_ARREST: '"Digital arrest" scam',
  UPI_PROBING: 'UPI ID probing',
}
