import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { formatTime, type BusySlot } from './api'
import Availability from './Availability'

// Monday 4 March 2030, 10:00 local time; only Date is faked so user-event timers keep working
const NOW = new Date(2030, 2, 4, 10, 0)
const at = (hour: number, minute = 0) => new Date(2030, 2, 4, hour, minute)
const slot = (d: Date) => screen.findByRole('button', { name: new RegExp(`^${formatTime(d)} `) })

const BUSY: BusySlot[] = [
  { startTime: at(12).toISOString(), endTime: at(13).toISOString(), status: 'CONFIRMED' },
  { startTime: at(14).toISOString(), endTime: at(14, 30).toISOString(), status: 'PENDING' },
]

describe('Availability', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(NOW)
    fetchMock = vi.fn(async () => new Response(JSON.stringify(BUSY), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('marks each slot as past, taken, pending, free or chosen', async () => {
    render(<Availability resourceId={7} selection={{ start: at(16), end: at(17) }} onPick={vi.fn()} />)

    expect(await slot(at(9, 30))).toHaveAccessibleName(`${formatTime(at(9, 30))} past`)
    expect(await slot(at(12, 30))).toHaveAccessibleName(`${formatTime(at(12, 30))} taken`)
    expect(await slot(at(14))).toHaveAccessibleName(`${formatTime(at(14))} held by a pending request`)
    expect(await slot(at(12, 30))).toBeDisabled()
    expect(await slot(at(15))).toBeEnabled()
    expect(await slot(at(16, 30))).toHaveAttribute('aria-pressed', 'true')
    expect(await slot(at(15))).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('button', { pressed: true, name: /2 taken/ })).toBeInTheDocument()
  })

  it('reports the clicked free slot', async () => {
    const onPick = vi.fn()
    render(<Availability resourceId={7} selection={{ start: null, end: null }} onPick={onPick} />)

    await userEvent.click(await slot(at(15)))

    expect(onPick).toHaveBeenCalledWith(at(15))
  })

  it('shows the reservation being edited as free', async () => {
    const editing = BUSY[0]
    render(<Availability resourceId={7} selection={{ start: null, end: null }} ignore={editing} onPick={vi.fn()} />)

    expect(await slot(at(12))).toHaveAccessibleName(`${formatTime(at(12))} free`)
  })

  it('loads the next week when moving forward', async () => {
    render(<Availability resourceId={7} selection={{ start: null, end: null }} onPick={vi.fn()} />)
    await slot(at(15))

    await userEvent.click(screen.getByRole('button', { name: 'Next week' }))

    const nextMonday = new Date(2030, 2, 11).toISOString()
    expect(fetchMock).toHaveBeenLastCalledWith(
      expect.stringContaining(`/api/resources/7/availability?from=${encodeURIComponent(nextMonday)}`), expect.anything())
    expect(screen.getByRole('button', { name: 'Previous week' })).toBeEnabled()
  })
})
