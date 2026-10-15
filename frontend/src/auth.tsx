import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, auth, post } from './api'

interface User { username: string; displayName: string; role: string }
interface AuthState { user: User | null; login: (u: string, p: string) => Promise<void>; logout: () => void; ready: boolean }

const Ctx = createContext<AuthState>(null!)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    if (!auth.token) { setReady(true); return }
    api<User>('/api/auth/me').then(setUser).catch(() => auth.clear()).finally(() => setReady(true))
  }, [])

  const login = async (username: string, password: string) => {
    const t = await post<{ accessToken: string; username: string; displayName: string; role: string }>(
      '/api/auth/login', { username, password })
    auth.set(t.accessToken)
    setUser({ username: t.username, displayName: t.displayName, role: t.role })
  }
  const logout = () => { auth.clear(); setUser(null) }
  return <Ctx.Provider value={{ user, login, logout, ready }}>{children}</Ctx.Provider>
}

export const useAuth = () => useContext(Ctx)
