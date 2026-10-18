export type Role = 'USER' | 'ADMIN'
export type ResourceType = 'MEETING_ROOM' | 'DESK' | 'EQUIPMENT' | 'VEHICLE'
export type ReservationStatus = 'CONFIRMED' | 'CANCELLED'

export const RESOURCE_TYPES: ResourceType[] = ['MEETING_ROOM', 'DESK', 'EQUIPMENT', 'VEHICLE']

export interface User { id: number; name: string; email: string; role: Role }
export interface AuthResponse { token: string; expiresAt: string; user: User }
export interface Resource {
  id: number; name: string; description: string | null; type: ResourceType; capacity: number; active: boolean
}
export interface Reservation {
  id: number; resourceId: number; resourceName: string; userId: number; userName: string
  startTime: string; endTime: string; status: ReservationStatus; createdAt: string
}
export interface Page<T> {
  content: T[]
  page: { size: number; number: number; totalElements: number; totalPages: number }
}

const TOKEN_KEY = 'auth'

// shortcut: token in localStorage is readable by any XSS on this origin; move to an httpOnly cookie for a real product
export function loadAuth(): AuthResponse | null {
  try {
    const auth = JSON.parse(localStorage.getItem(TOKEN_KEY) ?? 'null') as AuthResponse | null
    return auth && new Date(auth.expiresAt) > new Date() ? auth : null
  } catch {
    return null
  }
}

export function saveAuth(auth: AuthResponse | null) {
  if (auth) localStorage.setItem(TOKEN_KEY, JSON.stringify(auth))
  else localStorage.removeItem(TOKEN_KEY)
}

export class ApiError extends Error {
  status: number
  fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

export async function api<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  const token = loadAuth()?.token
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(`/api${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  if (res.status === 401 && token) {
    saveAuth(null)
    window.location.assign('/login')
  }
  if (!res.ok) {
    // Backend errors are RFC 9457 ProblemDetail: { detail, errors? }
    const problem = await res.json().catch(() => ({}))
    throw new ApiError(res.status, problem.detail ?? `Request failed (${res.status})`, problem.errors)
  }
  return (res.status === 204 ? undefined : await res.json()) as T
}

export function query(params: Record<string, string | number | undefined>) {
  const q = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => v !== undefined && v !== '' && q.set(k, String(v)))
  return q.toString()
}

export const formatDate = (iso: string) =>
  new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })

export const label = (value: string) => value.replace('_', ' ').toLowerCase()
