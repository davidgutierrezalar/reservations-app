export type Role = 'USER' | 'ADMIN'
export type ResourceType = 'MEETING_ROOM' | 'DESK' | 'EQUIPMENT' | 'VEHICLE'
export type ReservationStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED'
export type Frequency = 'DAILY' | 'WEEKLY'

export const RESOURCE_TYPES: ResourceType[] = ['MEETING_ROOM', 'DESK', 'EQUIPMENT', 'VEHICLE']
export const RESERVATION_STATUSES: ReservationStatus[] = ['PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED']
export const isActive = (s: ReservationStatus) => s === 'PENDING' || s === 'CONFIRMED'

export interface User { id: number; name: string; email: string; role: Role }
export interface Resource {
  id: number; name: string; description: string | null; type: ResourceType; capacity: number; active: boolean
  requiresApproval: boolean
}
export interface Reservation {
  id: number; resourceId: number; resourceName: string; userId: number; userName: string
  startTime: string; endTime: string; status: ReservationStatus; seriesId: string | null; createdAt: string
}
export interface BusySlot { startTime: string; endTime: string; status: ReservationStatus }
export interface AuditEntry {
  id: number; entityType: 'RESERVATION' | 'RESOURCE'; entityId: number; action: string
  actorId: number; actorName: string; details: string | null; createdAt: string
}
export interface Page<T> {
  content: T[]
  page: { size: number; number: number; totalElements: number; totalPages: number }
}

export class ApiError extends Error {
  status: number
  fieldErrors: Record<string, string>
  /** The whole ProblemDetail, for extra fields such as `conflicts` */
  data: Record<string, unknown>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}, data: Record<string, unknown> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
    this.data = data
  }
}

// The session lives in httpOnly cookies the browser sends by itself; scripts here never see the tokens
export async function api<T>(path: string, options: { method?: string; body?: unknown } = {}, retry = true): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  // Spring Security's CSRF check: echo the readable XSRF-TOKEN cookie on state-changing calls
  const xsrf = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)?.[1]
  if (method !== 'GET' && xsrf) headers['X-XSRF-TOKEN'] = decodeURIComponent(xsrf)

  const res = await fetch(`/api${path}`, {
    method,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  // The access token is short-lived: renew it once with the refresh cookie and replay the call
  if (res.status === 401 && retry && !path.startsWith('/auth/')) {
    if (await refreshSession()) return api<T>(path, options, false)
    window.location.assign('/login')
  }
  if (!res.ok) {
    // Backend errors are RFC 9457 ProblemDetail: { detail, errors? }
    const problem = await res.json().catch(() => ({}))
    throw new ApiError(res.status, problem.detail ?? `Request failed (${res.status})`, problem.errors, problem)
  }
  return (res.status === 204 ? undefined : await res.json()) as T
}

let refreshing: Promise<User | null> | null = null

/** Renews the cookies and returns who is signed in, or null. Parallel 401s share one refresh call. */
export function refreshSession() {
  refreshing ??= api<User>('/auth/refresh', { method: 'POST' })
    .catch(() => null)
    .finally(() => { refreshing = null })
  return refreshing
}

export function query(params: Record<string, string | number | undefined>) {
  const q = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => v !== undefined && v !== '' && q.set(k, String(v)))
  return q.toString()
}

export const formatDate = (iso: string) =>
  new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })

export const label = (value: string) => value.replace('_', ' ').toLowerCase()

export const formatTime = (d: Date | string) =>
  new Date(d).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })

export function errorText(err: unknown) {
  if (!(err instanceof ApiError)) return 'Could not reach the server'
  const fields = Object.values(err.fieldErrors ?? {}).join(', ')
  return fields ? `${err.message}: ${fields}` : err.message
}
