import { useState, type FormEvent } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { api, ApiError, type User } from '../api'
import { useAuth } from '../auth'

export default function LoginPage() {
  const { user, signIn } = useAuth()
  const [params] = useSearchParams()
  const [mode, setMode] = useState<'login' | 'register'>(params.get('mode') === 'register' ? 'register' : 'login')
  const [form, setForm] = useState({ name: '', email: '', password: '' })
  const [error, setError] = useState<ApiError | null>(null)
  const [loading, setLoading] = useState(false)

  if (user) return <Navigate to="/resources" replace />

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError(null)
    try {
      const body = mode === 'login' ? { email: form.email, password: form.password } : form
      signIn(await api<User>(`/auth/${mode}`, { method: 'POST', body }))
    } catch (err) {
      setError(err instanceof ApiError ? err : new ApiError(0, 'Could not reach the server'))
    } finally {
      setLoading(false)
    }
  }

  const field = (name: keyof typeof form, label: string, type = 'text') => (
    <label>
      {label}
      <input
        type={type}
        required
        value={form[name]}
        aria-invalid={Boolean(error?.fieldErrors[name])}
        aria-describedby={error?.fieldErrors[name] ? `${name}-error` : undefined}
        onChange={(e) => setForm({ ...form, [name]: e.target.value })}
      />
      {error?.fieldErrors[name] && <small className="error" id={`${name}-error`}>{error.fieldErrors[name]}</small>}
    </label>
  )

  return (
    <div className="card narrow">
      <Link to="/" className="brand">Slotline</Link>
      <h1>{mode === 'login' ? 'Sign in' : 'Create account'}</h1>
      <form onSubmit={submit} className="stack">
        {mode === 'register' && field('name', 'Name')}
        {field('email', 'Email', 'email')}
        {field('password', 'Password', 'password')}
        {error && <p className="error" role="alert">{error.message}</p>}
        <button type="submit" disabled={loading}>
          {mode === 'login' ? 'Sign in' : 'Register'}
        </button>
      </form>
      <button className="link" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(null) }}>
        {mode === 'login' ? 'No account? Register' : 'Have an account? Sign in'}
      </button>
    </div>
  )
}
