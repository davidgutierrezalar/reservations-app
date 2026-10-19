import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DateTimePicker from './DateTimePicker'

// Monday 4 March 2030, 10:00 local time; only Date is faked so user-event timers keep working
const NOW = new Date(2030, 2, 4, 10, 0)
const dayName = (d: Date) => d.toLocaleDateString(undefined, { dateStyle: 'full' })
const timeName = (d: Date) => d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })

describe('DateTimePicker', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(NOW)
  })
  afterEach(() => vi.useRealTimers())

  it('picks a day, then a time, and closes returning focus to the trigger', async () => {
    const onChange = vi.fn()
    render(<DateTimePicker label="Start" value={null} onChange={onChange} min={NOW} />)
    const trigger = screen.getByRole('button', { name: 'Start' })

    await userEvent.click(trigger)
    await userEvent.click(screen.getByRole('button', { name: dayName(new Date(2030, 2, 6)) }))
    await userEvent.click(screen.getByRole('button', { name: timeName(new Date(2030, 2, 6, 14, 30)) }))

    expect(onChange).toHaveBeenCalledWith(new Date(2030, 2, 6, 14, 30))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })

  it('disables days and times before the minimum', async () => {
    render(<DateTimePicker label="Start" value={null} onChange={vi.fn()} min={NOW} />)
    await userEvent.click(screen.getByRole('button', { name: 'Start' }))

    expect(screen.getByRole('button', { name: dayName(new Date(2030, 2, 3)) })).toBeDisabled()
    expect(screen.getByRole('button', { name: dayName(new Date(2030, 2, 4)) })).toBeEnabled()
    expect(screen.getByRole('button', { name: timeName(new Date(2030, 2, 4, 9, 30)) })).toBeDisabled()
    expect(screen.getByRole('button', { name: timeName(new Date(2030, 2, 4, 10, 0)) })).toBeEnabled()
  })

  it('closes with Escape without changing the value', async () => {
    const onChange = vi.fn()
    render(<DateTimePicker label="Start" value={null} onChange={onChange} min={NOW} />)
    await userEvent.click(screen.getByRole('button', { name: 'Start' }))
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    await userEvent.keyboard('{Escape}')

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(onChange).not.toHaveBeenCalled()
  })

  it('shows the chosen value on the trigger', () => {
    render(<DateTimePicker label="Start" value={new Date(2030, 2, 6, 14, 30)} onChange={vi.fn()} min={NOW} />)
    expect(screen.getByRole('button', { name: 'Start' })).toHaveAccessibleDescription(
      expect.stringContaining(timeName(new Date(2030, 2, 6, 14, 30))))
  })
})
