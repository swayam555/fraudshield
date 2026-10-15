import { useEffect, useRef, useState } from 'react'
import { auth, type LivePayment, type LiveStats } from './api'

export interface NewCase { id: number; subject: string; typology: string; priority: string }

/** Subscribes to the Server-Sent Events stream. EventSource reconnects by itself if it drops. */
export function useLiveFeed(maxPayments = 60) {
  const [stats, setStats] = useState<LiveStats | null>(null)
  const [payments, setPayments] = useState<LivePayment[]>([])
  const [cases, setCases] = useState<NewCase[]>([])
  const [connected, setConnected] = useState(false)
  const buffer = useRef<LivePayment[]>([])

  useEffect(() => {
    const es = new EventSource(`/api/stream/live?access_token=${encodeURIComponent(auth.token ?? '')}`)
    es.onopen = () => setConnected(true)
    es.onerror = () => setConnected(false)
    es.addEventListener('stats', e => setStats(JSON.parse((e as MessageEvent).data)))
    es.addEventListener('payment', e => { buffer.current.push(JSON.parse((e as MessageEvent).data)) })
    es.addEventListener('case', e => setCases(c => [JSON.parse((e as MessageEvent).data), ...c].slice(0, 8)))
    // Batch payment events into one render every 400 ms instead of one per event.
    const flush = setInterval(() => {
      if (buffer.current.length === 0) return
      const fresh = buffer.current.reverse()
      buffer.current = []
      setPayments(p => [...fresh, ...p].slice(0, maxPayments))
    }, 400)
    return () => { es.close(); clearInterval(flush) }
  }, [maxPayments])

  return { stats, payments, cases, connected }
}
