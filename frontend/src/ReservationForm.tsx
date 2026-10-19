import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError, errorText, formatDate, type Frequency, type Page, type Reservation, type Resource } from './api'
import Availability from './Availability'
import DateTimePicker from './DateTimePicker'

const HOUR = 60 * 60_000
const MAX_OCCURRENCES = 12

interface Props {
  resource: { id: number; name: string; requiresApproval?: boolean }
  /** When set, the form edits this reservation instead of creating one */
  existing?: Reservation
  onClose: () => void
  onSaved?: () => void
}

type Message = { ok: boolean; text: string; conflicts?: string[] }

export default function ReservationForm({ resource, existing, onClose, onSaved }: Props) {
  const [resourceId, setResourceId] = useState(resource.id)
  const [options, setOptions] = useState<Resource[]>([])
  const [start, setStart] = useState<Date | null>(existing ? new Date(existing.startTime) : null)
  const [end, setEnd] = useState<Date | null>(existing ? new Date(existing.endTime) : null)
  const [repeat, setRepeat] = useState<Frequency | ''>('')
  const [occurrences, setOccurrences] = useState(4)
  const [message, setMessage] = useState<Message | null>(null)
  const [saving, setSaving] = useState(false)
  const [version, setVersion] = useState(0)
  // Captured when the form opens; the server re-checks "in the future" on submit anyway
  const [now] = useState(() => new Date())

  useEffect(() => {
    if (!existing) return
    // shortcut: the resource switcher lists the first 100 active resources; add search if there are more
    api<Page<Resource>>('/resources?active=true&size=100').then((p) => setOptions(p.content), () => setOptions([]))
  }, [existing])

  const pickStart = (d: Date) => {
    const length = start && end ? end.getTime() - start.getTime() : HOUR
    setStart(d)
    // Keep the chosen length when moving the start; default to one hour
    setEnd(new Date(d.getTime() + length))
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!start || !end) return
    setSaving(true)
    const body = { resourceId, startTime: start.toISOString(), endTime: end.toISOString() }
    try {
      if (existing) {
        const saved = await api<Reservation>(`/reservations/${existing.id}`, { method: 'PUT', body })
        setMessage({ ok: true, text: saved.status === 'PENDING' ? 'Changes saved. The new time waits for approval.' : 'Changes saved.' })
      } else if (repeat) {
        const saved = await api<Reservation[]>('/reservations/series', { method: 'POST', body: { ...body, frequency: repeat, occurrences } })
        setMessage({ ok: true, text: `${saved.length} reservations created${saved[0]?.status === 'PENDING' ? ', waiting for approval' : ''}.` })
      } else {
        const saved = await api<Reservation>('/reservations', { method: 'POST', body })
        setMessage({
          ok: true,
          text: saved.status === 'PENDING'
            ? 'Request sent. An admin has to approve it before it is confirmed.'
            : 'Reservation confirmed. See it under My reservations.',
        })
      }
      setVersion((v) => v + 1)
      // Show the new booking as taken instead of still "selected"
      if (!existing) { setStart(null); setEnd(null) }
      onSaved?.()
    } catch (err) {
      const conflicts = err instanceof ApiError ? (err.data.conflicts as string[] | undefined) : undefined
      setMessage({ ok: false, text: errorText(err), conflicts })
    } finally {
      setSaving(false)
    }
  }

  const name = options.find((o) => o.id === resourceId)?.name ?? resource.name
  const needsApproval = options.find((o) => o.id === resourceId)?.requiresApproval ?? resource.requiresApproval

  return (
    <form className="card stack has-popover" onSubmit={submit}>
      <h2>{existing ? `Change reservation: ${name}` : `Book ${name}`}</h2>
      {needsApproval && <p className="muted">This resource needs an admin's approval. Your booking will stay pending until then.</p>}

      {existing && options.length > 0 && (
        <label>Resource
          <select value={resourceId} onChange={(e) => setResourceId(Number(e.target.value))}>
            {options.map((o) => <option key={o.id} value={o.id}>{o.name} ({o.capacity})</option>)}
          </select>
        </label>
      )}

      <Availability key={resourceId} resourceId={resourceId} selection={{ start, end }} onPick={pickStart} version={version}
                    ignore={existing && existing.resourceId === resourceId ? existing : undefined} />

      <div className="row">
        <DateTimePicker label="Start" value={start} onChange={pickStart} min={now} />
        <DateTimePicker label="End" value={end} onChange={setEnd}
                        min={start ? new Date(start.getTime() + 30 * 60_000) : now} />
      </div>

      {!existing && (
        <div className="row">
          <label>Repeat
            <select value={repeat} onChange={(e) => setRepeat(e.target.value as Frequency | '')}>
              <option value="">Does not repeat</option>
              <option value="DAILY">Every day</option>
              <option value="WEEKLY">Every week</option>
            </select>
          </label>
          {repeat && (
            <label>Times
              <select value={occurrences} onChange={(e) => setOccurrences(Number(e.target.value))}>
                {Array.from({ length: MAX_OCCURRENCES - 1 }, (_, i) => i + 2).map((n) => <option key={n} value={n}>{n}</option>)}
              </select>
            </label>
          )}
        </div>
      )}
      {repeat && <p className="muted">If any of the dates is taken, nothing is booked and you will see which ones collide.</p>}

      {message && (
        <div className={message.ok ? 'success' : 'error'} role="status">
          {message.text}
          {message.conflicts && (
            <ul>{message.conflicts.map((c) => <li key={c}>{formatDate(c)}</li>)}</ul>
          )}
        </div>
      )}
      <div className="row">
        <button type="submit" disabled={!start || !end || saving}>
          {existing ? 'Save changes' : repeat ? `Book ${occurrences} times` : 'Confirm booking'}
        </button>
        <button type="button" className="link" onClick={onClose}>Close</button>
      </div>
    </form>
  )
}
