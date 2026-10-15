import { useEffect } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { MotionConfig } from 'motion/react'
import { AuthProvider, useAuth } from './auth'
import Layout from './components/Layout'
import Landing from './landing/Landing'
import Login from './pages/Login'
import Monitor from './pages/Monitor'
import Cases from './pages/Cases'
import CaseDetail from './pages/CaseDetail'
import Playground from './pages/Playground'
import Model from './pages/Model'
import Knowledge from './pages/Knowledge'

function Protected() {
  const { user, ready } = useAuth()
  if (!ready) return null
  return user ? <Layout /> : <Navigate to="/login" replace />
}

function ScrollToTop() {
  const { pathname, hash } = useLocation()
  useEffect(() => { if (!hash) window.scrollTo(0, 0) }, [pathname, hash])
  return null
}

export default function App() {
  return (
    <MotionConfig reducedMotion="user">
      <AuthProvider>
        <BrowserRouter>
          <ScrollToTop />
          <Routes>
            <Route path="/" element={<Landing />} />
            <Route path="/login" element={<Login />} />
            <Route path="/app" element={<Protected />}>
              <Route index element={<Monitor />} />
              <Route path="cases" element={<Cases />} />
              <Route path="cases/:id" element={<CaseDetail />} />
              <Route path="playground" element={<Playground />} />
              <Route path="model" element={<Model />} />
              <Route path="knowledge" element={<Knowledge />} />
            </Route>
            {/* Old URLs from before the console moved under /app */}
            <Route path="/cases/*" element={<Navigate to="/app/cases" replace />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </MotionConfig>
  )
}
