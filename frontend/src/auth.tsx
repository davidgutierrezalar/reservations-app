import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, refreshSession, type User } from './api'

interface AuthContextValue {
  user: User | null
  signIn: (user: User) => void
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  // undefined until the first refresh answers: the cookies are httpOnly, so asking the API is the only way to know
  const [user, setUser] = useState<User | null | undefined>(undefined)

  useEffect(() => {
    refreshSession().then(setUser)
  }, [])

  const signOut = async () => {
    await api('/auth/logout', { method: 'POST' }).catch(() => undefined)
    setUser(null)
  }

  if (user === undefined) return null
  return <AuthContext.Provider value={{ user, signIn: setUser, signOut }}>{children}</AuthContext.Provider>
}

// Hook lives next to its provider on purpose; only costs fast refresh for this file
// oxlint-disable-next-line react/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
