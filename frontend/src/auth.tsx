import { createContext, useContext, useState, type ReactNode } from 'react'
import { loadAuth, saveAuth, type AuthResponse, type User } from './api'

interface AuthContextValue {
  user: User | null
  signIn: (auth: AuthResponse) => void
  signOut: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => loadAuth()?.user ?? null)

  const signIn = (auth: AuthResponse) => {
    saveAuth(auth)
    setUser(auth.user)
  }
  const signOut = () => {
    saveAuth(null)
    setUser(null)
  }

  return <AuthContext.Provider value={{ user, signIn, signOut }}>{children}</AuthContext.Provider>
}

// Hook lives next to its provider on purpose; only costs fast refresh for this file
// oxlint-disable-next-line react/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
