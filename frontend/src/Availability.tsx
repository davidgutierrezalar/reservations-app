import { useEffect, useState } from 'react'
import { api, formatTime, query, type BusySlot } from './api'

const SLOT_MS = 30 * 60_000
const DAY_MS = 24 * 60 * 60_000
// shortcut: shows 06:00-22:00 only; bookings outside it are still possible through the pickers below
const FIRST_HOUR = 6
const LAST_HOUR = 22

const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate())

interface Props {
  resourceId: number
  /** Highlighted range, e.g. the times currently chosen in the form */
  selection: { start: Date | null; end: Date | null }
  /** The reservation being edited: shown as yours, not as taken */
  ignore?: { startTime: string; endTime: string }
  onPick: (start: Date) => void
  /** Bump to refetch, e.g. after a booking was saved */
  version?: number
}

/** Week strip plus the chosen day's half-hour grid, so free gaps are visible before picking a time. */
export default function Availability({ resourceId, selection, ignore, onPick, version }: Props) {
  const [now] = useState(() => new Date())
  const [weekStart, setWeekStart] = useState(() => startOfDay(selection.start ?? now))
  const [day, setDay] = useState(() => startOfDay(selection.start ?? now))
  const [busy, setBusy] = useState<BusySlot[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    const to = new Date(weekStart.getTime() + 7 * DAY_MS)
    api<BusySlot[]>(`/resources/${resourceId}/availability?${query({ from: weekStart.toISOString(), to: to.toISOString() })}`)
      .then((slots) => { setBusy(slots); setError('') }, (e: Error) => setError(e.message))
  }, [resourceId, weekStart, version])

  const taken = (busy ?? []).filter((b) => !(ignore && b.startTime === ignore.startTime && b.endTime === ignore.endTime))
  const isTaken = (from: number, to: number) =>
    taken.find((b) => new Date(b.startTime).getTime() < to && new Date(b.endTime).getTime() > from)

  const days = Array.from({ length: 7 }, (_, i) => new Date(weekStart.getFullYear(), weekStart.getMonth(), weekStart.getDate() + i))
  const slots = Array.from({ length: (LAST_HOUR - FIRST_HOUR) * 2 }, (_, i) =>
    new Date(day.getFullYear(), day.getMonth(), day.getDate(), FIRST_HOUR, i * 30))
  const selStart = selection.start?.getTime() ?? -1
  const selEnd = selection.end?.getTime() ?? -1
  const canGoBack = weekStart > startOfDay(now)

  const moveWeek = (weeks: number) => {
    const next = new Date(Math.max(startOfDay(now).getTime(), weekStart.getTime() + weeks * 7 * DAY_MS))
    setWeekStart(startOfDay(next))
    setDay(startOfDay(next))
  }

  return (
    <section className="avail" aria-label="Availability">
      <div className="avail-week">
        <button type="button" className="secondary" aria-label="Previous week" disabled={!canGoBack} onClick={() => moveWeek(-1)}>‹</button>
        <div className="avail-days" role="group" aria-label="Day">
          {days.map((d) => {
            const dayTaken = taken.filter((b) =>
              new Date(b.startTime).getTime() < d.getTime() + DAY_MS && new Date(b.endTime).getTime() > d.getTime()).length
            return (
              <button key={d.getTime()} type="button" className="avail-day" aria-pressed={d.getTime() === day.getTime()}
                      onClick={() => setDay(d)}>
                <span>{d.toLocaleDateString(undefined, { weekday: 'short' })}</span>
                <strong>{d.getDate()}</strong>
                <small>{dayTaken ? `${dayTaken} taken` : 'free'}</small>
              </button>
            )
          })}
        </div>
        <button type="button" className="secondary" aria-label="Next week" onClick={() => moveWeek(1)}>›</button>
      </div>

      {error && <p className="error" role="alert">{error}</p>}
      <div className="avail-grid" aria-busy={busy === null}>
        {slots.map((s) => {
          const from = s.getTime()
          const hit = isTaken(from, from + SLOT_MS)
          const past = from < now.getTime()
          // Overlap, not containment: times that are not on a half-hour boundary still light their slots
          const chosen = from < selEnd && from + SLOT_MS > selStart
          const state = hit ? (hit.status === 'PENDING' ? 'pending' : 'taken') : past ? 'past' : 'free'
          return (
            <button key={from} type="button" className={`slot ${state}${chosen ? ' chosen' : ''}`}
                    disabled={state !== 'free'} aria-pressed={chosen}
                    aria-label={`${formatTime(s)} ${state === 'pending' ? 'held by a pending request' : state}`}
                    onClick={() => onPick(s)}>
              {formatTime(s)}
            </button>
          )
        })}
      </div>
      <p className="avail-legend muted">
        <span className="key free" /> free <span className="key taken" /> booked
        <span className="key pending" /> waiting for approval <span className="key chosen" /> your selection
      </p>
    </section>
  )
}
