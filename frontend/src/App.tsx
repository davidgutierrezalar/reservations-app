import type { ReactNode } from 'react'
import { BrowserRouter, Link, Navigate, NavLink, Outlet, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth'
import ActivityPage from './pages/ActivityPage'
import EmailPreviewPage from './pages/EmailPreviewPage'
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import ReservationsPage from './pages/ReservationsPage'
import ResourcesPage from './pages/ResourcesPage'

function RequireAuth({ children, admin = false }: { children: ReactNode; admin?: boolean }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  // The API enforces roles too; this only keeps users away from screens that would just show 403s
  return admin && user.role !== 'ADMIN' ? <Navigate to="/resources" replace /> : children
}

// Signed-in pages share the header and the centered container; the landing page has its own layout
function AppLayout() {
  return (
    <>
      <Header />
      <main className="container">
        <Outlet />
      </main>
    </>
  )
}

function Header() {
  const { user, signOut } = useAuth()
  if (!user) return null
  return (
    <header className="header">
      <Link to="/" className="brand">Slotline</Link>
      <nav>
        <NavLink to="/resources">Resources</NavLink>
        <NavLink to="/reservations" end>{user.role === 'ADMIN' ? 'All reservations' : 'My reservations'}</NavLink>
        {user.role === 'ADMIN' && <NavLink to="/approvals">Approvals</NavLink>}
        {user.role === 'ADMIN' && <NavLink to="/activity">Activity</NavLink>}
      </nav>
      <span className="who">
        {user.name} ({user.role.toLowerCase()})
      </span>
      <button className="link" onClick={signOut}>
        Sign out
      </button>
    </header>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route element={<AppLayout />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/resources" element={<RequireAuth><ResourcesPage /></RequireAuth>} />
            <Route path="/reservations" element={<RequireAuth><ReservationsPage /></RequireAuth>} />
            <Route path="/email-previews" element={<RequireAuth><EmailPreviewPage /></RequireAuth>} />
            <Route path="/approvals" element={<RequireAuth admin><ReservationsPage key="approvals" approvals /></RequireAuth>} />
            <Route path="/activity" element={<RequireAuth admin><ActivityPage /></RequireAuth>} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
