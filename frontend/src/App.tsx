import type { ReactNode } from 'react'
import { BrowserRouter, Link, Navigate, NavLink, Outlet, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth'
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import ReservationsPage from './pages/ReservationsPage'
import ResourcesPage from './pages/ResourcesPage'

function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  return user ? children : <Navigate to="/login" replace />
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
        <NavLink to="/reservations">{user.role === 'ADMIN' ? 'All reservations' : 'My reservations'}</NavLink>
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
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
