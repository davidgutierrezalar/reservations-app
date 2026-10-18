import type { ReactNode } from 'react'
import { BrowserRouter, Navigate, NavLink, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth'
import LoginPage from './pages/LoginPage'
import ReservationsPage from './pages/ReservationsPage'
import ResourcesPage from './pages/ResourcesPage'

function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  return user ? children : <Navigate to="/login" replace />
}

function Header() {
  const { user, signOut } = useAuth()
  if (!user) return null
  return (
    <header className="header">
      <strong>Reservations</strong>
      <nav>
        <NavLink to="/resources">Resources</NavLink>
        <NavLink to="/reservations">{user.role === 'ADMIN' ? 'All reservations' : 'My reservations'}</NavLink>
      </nav>
      <span className="muted">
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
        <Header />
        <main className="container">
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/resources" element={<RequireAuth><ResourcesPage /></RequireAuth>} />
            <Route path="/reservations" element={<RequireAuth><ReservationsPage /></RequireAuth>} />
            <Route path="*" element={<Navigate to="/resources" replace />} />
          </Routes>
        </main>
      </BrowserRouter>
    </AuthProvider>
  )
}
