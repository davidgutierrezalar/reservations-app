import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, ApiError, label, query, RESOURCE_TYPES, type Page, type Resource, type ResourceType } from '../api'
import { useAuth } from '../auth'
import Pager from '../Pager'

export default function ResourcesPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const [name, setName] = useState('')
  const [type, setType] = useState<ResourceType | ''>('')
  const [pageNumber, setPageNumber] = useState(0)
  const [data, setData] = useState<Page<Resource> | null>(null)
  const [error, setError] = useState('')
  const [booking, setBooking] = useState<Resource | null>(null)

  const load = useCallback(() => {
    // Regular users only see bookable resources; admins also see deactivated ones
    const qs = query({ name, type, page: pageNumber, size: 10, active: isAdmin ? undefined : 'true' })
    api<Page<Resource>>(`/resources?${qs}`).then(setData, (e: Error) => setError(e.message))
  }, [name, type, pageNumber, isAdmin])

  useEffect(load, [load])

  const deactivate = async (r: Resource) => {
    if (!confirm(`Deactivate "${r.name}"? Users will no longer be able to book it.`)) return
    await api(`/resources/${r.id}`, { method: 'DELETE' }).then(load, (e: Error) => setError(e.message))
  }

  return (
    <>
      <h1>Resources</h1>
      <div className="filters">
        <input placeholder="Search by name" value={name}
               onChange={(e) => { setName(e.target.value); setPageNumber(0) }} />
        <select value={type} onChange={(e) => { setType(e.target.value as ResourceType | ''); setPageNumber(0) }}>
          <option value="">All types</option>
          {RESOURCE_TYPES.map((t) => <option key={t} value={t}>{label(t)}</option>)}
        </select>
      </div>
      {error && <p className="error" role="alert">{error}</p>}

      {isAdmin && <CreateResourceForm onCreated={load} />}
      {booking && <BookingForm resource={booking} onClose={() => setBooking(null)} />}

      <table>
        <thead>
          <tr><th>Name</th><th>Type</th><th>Capacity</th>{isAdmin && <th>Status</th>}<th /></tr>
        </thead>
        <tbody>
          {data?.content.map((r) => (
            <tr key={r.id}>
              <td>{r.name}{r.description && <div className="muted">{r.description}</div>}</td>
              <td>{label(r.type)}</td>
              <td>{r.capacity}</td>
              {isAdmin && <td>{r.active ? 'active' : 'inactive'}</td>}
              <td className="actions">
                {r.active && <button onClick={() => setBooking(r)}>Book</button>}
                {isAdmin && r.active && <button className="danger" onClick={() => deactivate(r)}>Deactivate</button>}
              </td>
            </tr>
          ))}
          {data?.content.length === 0 && <tr><td colSpan={5} className="muted">No resources found</td></tr>}
        </tbody>
      </table>
      {data && <Pager page={data.page} onChange={setPageNumber} />}
    </>
  )
}

function BookingForm({ resource, onClose }: { resource: Resource; onClose: () => void }) {
  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    try {
      // datetime-local has no timezone: Date reads it as local time, toISOString sends UTC
      await api('/reservations', {
        method: 'POST',
        body: { resourceId: resource.id, startTime: new Date(start).toISOString(), endTime: new Date(end).toISOString() },
      })
      setMessage({ ok: true, text: 'Reservation confirmed. See it under My reservations.' })
    } catch (err) {
      const apiErr = err as ApiError
      const fields = Object.values(apiErr.fieldErrors ?? {}).join(', ')
      setMessage({ ok: false, text: fields ? `${apiErr.message}: ${fields}` : apiErr.message })
    }
  }

  return (
    <form className="card stack" onSubmit={submit}>
      <h2>Book {resource.name}</h2>
      <div className="row">
        <label>Start<input type="datetime-local" required value={start} onChange={(e) => setStart(e.target.value)} /></label>
        <label>End<input type="datetime-local" required value={end} onChange={(e) => setEnd(e.target.value)} /></label>
      </div>
      {message && <p className={message.ok ? 'success' : 'error'} role="status">{message.text}</p>}
      <div className="row">
        <button type="submit">Confirm booking</button>
        <button type="button" className="link" onClick={onClose}>Close</button>
      </div>
    </form>
  )
}

function CreateResourceForm({ onCreated }: { onCreated: () => void }) {
  const empty = { name: '', description: '', type: 'MEETING_ROOM' as ResourceType, capacity: 1 }
  const [form, setForm] = useState(empty)
  const [error, setError] = useState<ApiError | null>(null)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    try {
      await api('/resources', { method: 'POST', body: form })
      setForm(empty)
      setError(null)
      onCreated()
    } catch (err) {
      setError(err as ApiError)
    }
  }

  return (
    <details className="card">
      <summary>New resource</summary>
      <form className="stack" onSubmit={submit}>
        <div className="row">
          <label>Name<input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
          <label>Type
            <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as ResourceType })}>
              {RESOURCE_TYPES.map((t) => <option key={t} value={t}>{label(t)}</option>)}
            </select>
          </label>
          <label>Capacity
            <input type="number" min={1} max={1000} required value={form.capacity}
                   onChange={(e) => setForm({ ...form, capacity: Number(e.target.value) })} />
          </label>
        </div>
        <label>Description<input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></label>
        {error && <p className="error" role="alert">{error.message} {Object.values(error.fieldErrors ?? {}).join(', ')}</p>}
        <button type="submit">Create</button>
      </form>
    </details>
  )
}
