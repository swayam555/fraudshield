import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { motion } from 'motion/react'
import { Activity, BookOpen, BrainCircuit, FolderSearch, LogOut, Send } from 'lucide-react'
import clsx from 'clsx'
import { useAuth } from '../auth'
import { Logo } from './ui'

const NAV = [
  { to: '/app', label: 'Live monitor', icon: Activity, end: true },
  { to: '/app/cases', label: 'Cases', icon: FolderSearch },
  { to: '/app/playground', label: 'Try a payment', icon: Send },
  { to: '/app/model', label: 'Risk model', icon: BrainCircuit },
  { to: '/app/knowledge', label: 'Knowledge base', icon: BookOpen },
]

export default function Layout() {
  const { user, logout } = useAuth()
  const location = useLocation()
  const initials = user?.displayName.split(' ').map(p => p[0]).join('').slice(0, 2)
  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[232px_1fr] lg:bg-[linear-gradient(to_right,#0d1013_232px,transparent_232px)]">
      <aside className="sticky top-0 hidden h-screen flex-col border-r border-line bg-[#0d1013] lg:flex">
        <NavLink to="/" className="flex h-16 items-center px-5"><Logo /></NavLink>
        <div className="px-5 pb-2 font-mono text-[10px] uppercase tracking-[0.16em] text-faint">Operations</div>
        <nav className="flex-1 space-y-0.5 px-3">
          {NAV.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end}
              className={({ isActive }) => clsx('focus-ring relative flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm transition',
                isActive ? 'text-fg' : 'text-muted hover:text-fg')}>
              {({ isActive }) => (<>
                {isActive && <motion.span layoutId="nav-active" className="absolute inset-0 rounded-lg bg-white/[0.06] ring-1 ring-inset ring-white/[0.06]" transition={{ type: 'spring', stiffness: 500, damping: 40 }} />}
                {isActive && <motion.span layoutId="nav-bar" className="absolute -left-3 top-2 bottom-2 w-0.5 rounded-full bg-signal" />}
                <Icon className="relative h-4 w-4" strokeWidth={1.75} /><span className="relative">{label}</span>
              </>)}
            </NavLink>
          ))}
        </nav>
        <div className="flex items-center gap-3 border-t border-line p-4">
          <span className="flex h-8 w-8 items-center justify-center rounded-full bg-signal/15 font-mono text-xs font-medium text-signal">{initials}</span>
          <div className="min-w-0 flex-1">
            <div className="truncate text-sm text-fg">{user?.displayName}</div>
            <div className="text-xs text-muted">{user?.role === 'LEAD' ? 'Fraud team lead' : 'Fraud analyst'}</div>
          </div>
          <button onClick={logout} title="Sign out" className="focus-ring rounded-md p-1.5 text-muted hover:bg-white/5 hover:text-fg"><LogOut className="h-4 w-4" /></button>
        </div>
      </aside>
      <div className="min-w-0">
        <div className="flex items-center gap-2 overflow-x-auto border-b border-line px-4 py-2.5 lg:hidden">
          <Logo compact />
          {NAV.map(({ to, label, end }) => (
            <NavLink key={to} to={to} end={end} className={({ isActive }) => clsx('whitespace-nowrap rounded-md px-2.5 py-1.5 text-sm', isActive ? 'bg-white/10 text-fg' : 'text-muted')}>{label}</NavLink>
          ))}
        </div>
        <motion.main key={location.pathname} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }}
            className="mx-auto max-w-[1480px] px-5 py-7 lg:px-8">
            <Outlet />
          </motion.main>
      </div>
    </div>
  )
}

export function PageHeader({ title, subtitle, action }: { title: string; subtitle?: string; action?: React.ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-[22px] font-semibold tracking-tight text-fg">{title}</h1>
        {subtitle && <p className="mt-1 max-w-3xl text-sm leading-relaxed text-muted">{subtitle}</p>}
      </div>
      {action}
    </div>
  )
}
