import { useEffect, useRef, useState } from 'react'

const SLOT_MINUTES = 30
const WEEKDAYS = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su']

const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate())
const sameDay = (a: Date, b: Date) => startOfDay(a).getTime() === startOfDay(b).getTime()
const timeLabel = (d: Date) => d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })

interface Props {
  label: string
  value: Date | null
  onChange: (value: Date) => void
  /** Earliest selectable moment; earlier days and time slots are disabled. */
  min: Date
}

/** Pick a day, then a time slot; the popover closes as soon as the time is chosen. */
export default function DateTimePicker({ label, value, onChange, min }: Props) {
  const [open, setOpen] = useState(false)
  const [today] = useState(() => startOfDay(new Date()))
  const [day, setDay] = useState<Date>(() => startOfDay(value ?? min))
  const [month, setMonth] = useState(() => new Date(day.getFullYear(), day.getMonth(), 1))
  const rootRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const timesRef = useRef<HTMLDivElement>(null)

  const show = () => {
    const d = startOfDay(value ?? min)
    setDay(d)
    setMonth(new Date(d.getFullYear(), d.getMonth(), 1))
    setOpen(true)
  }
  const close = () => {
    setOpen(false)
    triggerRef.current?.focus()
  }

  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    // Center the selected (or first available) slot by scrolling only the list, never the page
    const list = timesRef.current
    const slot = list?.querySelector<HTMLElement>('[aria-pressed="true"]') ?? list?.querySelector<HTMLElement>('button:not(:disabled)')
    if (list && slot) list.scrollTop = slot.offsetTop - list.offsetTop - list.clientHeight / 2 + slot.clientHeight / 2
    return () => document.removeEventListener('mousedown', onDown)
  }, [open, day])

  // Monday-first grid: blanks before the 1st, then each day of the month
  const offset = (month.getDay() + 6) % 7
  const daysInMonth = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate()
  const cells = [
    ...Array<null>(offset).fill(null),
    ...Array.from({ length: daysInMonth }, (_, i) => new Date(month.getFullYear(), month.getMonth(), i + 1)),
  ]
  const minDay = startOfDay(min)
  const canGoBack = month > new Date(minDay.getFullYear(), minDay.getMonth(), 1)

  const slots = Array.from({ length: (24 * 60) / SLOT_MINUTES }, (_, i) =>
    new Date(day.getFullYear(), day.getMonth(), day.getDate(), 0, i * SLOT_MINUTES))

  const display = value
    ? `${value.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })}, ${timeLabel(value)}`
    : 'Choose day and time'

  return (
    <div className="dtp" ref={rootRef} onKeyDown={(e) => e.key === 'Escape' && open && close()}>
      <span className="dtp-label" id={`${label}-label`}>{label}</span>
      <button type="button" ref={triggerRef} className={`dtp-trigger${value ? '' : ' empty'}`}
              aria-haspopup="dialog" aria-expanded={open} aria-labelledby={`${label}-label`}
              aria-describedby={`${label}-value`} onClick={() => (open ? setOpen(false) : show())}>
        <span id={`${label}-value`}>{display}</span>
      </button>

      {open && (
        <div className="dtp-pop" role="dialog" aria-label={`${label}: choose day and time`}>
          <div>
            <div className="dtp-month">
              <button type="button" className="secondary" aria-label="Previous month" disabled={!canGoBack}
                      onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))}>‹</button>
              <span aria-live="polite">{month.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })}</span>
              <button type="button" className="secondary" aria-label="Next month"
                      onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))}>›</button>
            </div>
            <div className="dtp-grid">
              {WEEKDAYS.map((w) => <span key={w} className="dtp-dow" aria-hidden>{w}</span>)}
              {cells.map((d, i) => d === null ? <span key={`b${i}`} /> : (
                <button key={d.getDate()} type="button"
                        className={`dtp-day${sameDay(d, today) ? ' today' : ''}`}
                        aria-pressed={sameDay(d, day)} disabled={d < minDay}
                        aria-label={d.toLocaleDateString(undefined, { dateStyle: 'full' })}
                        autoFocus={sameDay(d, day)} onClick={() => setDay(d)}>
                  {d.getDate()}
                </button>
              ))}
            </div>
          </div>
          <div>
            <div className="dtp-times-title">
              {day.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric' })}
            </div>
            <div className="dtp-times" ref={timesRef}>
              {slots.map((t) => (
                <button key={t.getTime()} type="button" className="dtp-time" disabled={t < min}
                        aria-pressed={value?.getTime() === t.getTime()}
                        onClick={() => { onChange(t); close() }}>
                  {timeLabel(t)}
                </button>
              ))}
            </div>
          </div>
          <p className="dtp-hint">Pick a day, then a time. Esc closes.</p>
        </div>
      )}
    </div>
  )
}
