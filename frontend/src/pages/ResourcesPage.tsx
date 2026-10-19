import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, errorText, label, query, RESOURCE_TYPES, type Page, type Resource, type ResourceType } from '../api'
import { useAuth } from '../auth'
import Pager from '../Pager'
import ReservationForm from '../ReservationForm'

export default function ResourcesPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const [name, setName] = useState('')
  const [type, setType] = useState<ResourceType | ''>('')
  const [minCapacity, setMinCapacity] = useState('')
  const [pageNumber, setPageNumber] = useState(0)
  const [data, setData] = useState<Page<Resource> | null>(null)
  const [error, setError] = useState('')
  const [booking, setBooking] = useState<Resource | null>(null)
  const [editing, setEditing] = useState<Resource | null>(null)

  const load = useCallback(() => {
    // Regular users only see bookable resources; admins also see deactivated ones
    const qs = query({ name, type, minCapacity, page: pageNumber, size: 10, active: isAdmin ? undefined : 'true' })
    api<Page<Resource>>(`/resources?${qs}`).then((p) => { setData(p); setError('') }, (e: Error) => setError(e.message))
  }, [name, type, minCapacity, pageNumber, isAdmin])

  useEffect(load, [load])

  const deactivate = async (r: Resource) => {
    if (!confirm(`Deactivate "${r.name}"? Users will no longer be able to book it.`)) return
    await api(`/resources/${r.id}`, { method: 'DELETE' }).then(load, (e: Error) => setError(e.message))
  }

  return (
    <>
      <h1>Resources</h1>
      <div className="filters">
        <label>Name
          <input placeholder="Search by name" value={name}
                 onChange={(e) => { setName(e.target.value); setPageNumber(0) }} />
        </label>
        <label>Type
          <select value={type} onChange={(e) => { setType(e.target.value as ResourceType | ''); setPageNumber(0) }}>
            <option value="">All types</option>
            {RESOURCE_TYPES.map((t) => <option key={t} value={t}>{label(t)}</option>)}
          </select>
        </label>
        <label>People, at least
          <input type="number" min={1} max={1000} placeholder="Any" value={minCapacity}
                 onChange={(e) => { setMinCapacity(e.target.value); setPageNumber(0) }} />
        </label>
      </div>
      {error && <p className="error" role="alert">{error}</p>}

      {isAdmin && !editing && (
        <details className="card">
          <summary>New resource</summary>
          <ResourceForm onSaved={load} />
        </details>
      )}
      {isAdmin && editing && (
        <div className="card">
          <h2>Edit {editing.name}</h2>
          <ResourceForm key={editing.id} existing={editing} onSaved={() => { setEditing(null); load() }}
                        onCancel={() => setEditing(null)} />
        </div>
      )}
      {booking && <ReservationForm key={booking.id} resource={booking} onClose={() => setBooking(null)} />}

      <div className="table-wrap">
        <table>
          <thead>
            <tr><th>Name</th><th>Type</th><th>Capacity</th>{isAdmin && <th>Status</th>}<th /></tr>
          </thead>
          <tbody>
            {data?.content.map((r) => (
              <tr key={r.id}>
                <td>
                  {r.name}
                  {r.requiresApproval && <span className="tag">needs approval</span>}
                  {r.description && <div className="muted">{r.description}</div>}
                </td>
                <td>{label(r.type)}</td>
                <td><CapacityMeter value={r.capacity} /></td>
                {isAdmin && <td>{r.active ? 'active' : 'inactive'}</td>}
                <td className="actions">
                  {r.active && <button onClick={() => setBooking(r)}>Book</button>}
                  {isAdmin && <button className="secondary" onClick={() => setEditing(r)}>Edit</button>}
                  {isAdmin && r.active && <button className="danger" onClick={() => deactivate(r)}>Deactivate</button>}
                </td>
              </tr>
            ))}
            {data?.content.length === 0 && (
              <tr><td colSpan={5} className="muted">No resources match these filters. Try a lower capacity or another type.</td></tr>
            )}
          </tbody>
        </table>
      </div>
      {data && <Pager page={data.page} onChange={setPageNumber} />}
    </>
  )
}

/** Seats as a small bar, so big and small spaces stand apart while scanning the list. */
function CapacityMeter({ value }: { value: number }) {
  const width = Math.min(100, Math.round((Math.log10(value) / Math.log10(50)) * 100))
  return (
    <span className="capacity" title={`${value} people`}>
      <span className="capacity-bar" aria-hidden><span style={{ width: `${Math.max(width, 6)}%` }} /></span>
      {value} {value === 1 ? 'person' : 'people'}
    </span>
  )
}

function ResourceForm({ existing, onSaved, onCancel }: { existing?: Resource; onSaved: () => void; onCancel?: () => void }) {
  const empty = { name: '', description: '', type: 'MEETING_ROOM' as ResourceType, capacity: 1, requiresApproval: false }
  const [form, setForm] = useState(existing
    ? { name: existing.name, description: existing.description ?? '', type: existing.type, capacity: existing.capacity, requiresApproval: existing.requiresApproval }
    : empty)
  const [error, setError] = useState('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    try {
      await api(existing ? `/resources/${existing.id}` : '/resources', { method: existing ? 'PUT' : 'POST', body: form })
      if (!existing) setForm(empty)
      setError('')
      onSaved()
    } catch (err) {
      setError(errorText(err))
    }
  }

  return (
    <form className="stack" onSubmit={submit}>
      <div className="row">
        <label>Name<input required maxLength={100} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
        <label>Type
          <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as ResourceType })}>
            {RESOURCE_TYPES.map((t) => <option key={t} value={t}>{label(t)}</option>)}
          </select>
        </label>
        <label>Capacity (people)
          <input type="number" min={1} max={1000} required value={form.capacity}
                 onChange={(e) => setForm({ ...form, capacity: Number(e.target.value) })} />
        </label>
      </div>
      <label>Description<input maxLength={500} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></label>
      <label className="check">
        <input type="checkbox" checked={form.requiresApproval}
               onChange={(e) => setForm({ ...form, requiresApproval: e.target.checked })} />
        Bookings need an admin's approval
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="row">
        <button type="submit">{existing ? 'Save changes' : 'Create resource'}</button>
        {onCancel && <button type="button" className="link" onClick={onCancel}>Cancel</button>}
      </div>
    </form>
  )
}
