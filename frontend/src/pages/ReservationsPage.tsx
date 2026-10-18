import { useCallback, useEffect, useState } from 'react'
import { api, formatDate, query, type Page, type Reservation, type ReservationStatus } from '../api'
import { useAuth } from '../auth'
import Pager from '../Pager'

export default function ReservationsPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const [status, setStatus] = useState<ReservationStatus | ''>('')
  const [pageNumber, setPageNumber] = useState(0)
  const [data, setData] = useState<Page<Reservation> | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    const path = isAdmin ? '/reservations' : '/reservations/me'
    api<Page<Reservation>>(`${path}?${query({ status, page: pageNumber, size: 10 })}`)
      .then(setData, (e: Error) => setError(e.message))
  }, [isAdmin, status, pageNumber])

  useEffect(load, [load])

  const cancel = async (r: Reservation) => {
    if (!confirm(`Cancel the reservation of ${r.resourceName} on ${formatDate(r.startTime)}?`)) return
    await api(`/reservations/${r.id}/cancel`, { method: 'PATCH' }).then(load, (e: Error) => setError(e.message))
  }

  return (
    <>
      <h1>{isAdmin ? 'All reservations' : 'My reservations'}</h1>
      <div className="filters">
        <select value={status} onChange={(e) => { setStatus(e.target.value as ReservationStatus | ''); setPageNumber(0) }}>
          <option value="">All statuses</option>
          <option value="CONFIRMED">Confirmed</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="table-wrap">
      <table>
        <thead>
          <tr><th>Resource</th>{isAdmin && <th>User</th>}<th>Start</th><th>End</th><th>Status</th><th /></tr>
        </thead>
        <tbody>
          {data?.content.map((r) => (
            <tr key={r.id}>
              <td>{r.resourceName}</td>
              {isAdmin && <td>{r.userName}</td>}
              <td>{formatDate(r.startTime)}</td>
              <td>{formatDate(r.endTime)}</td>
              <td><span className={`badge ${r.status.toLowerCase()}`}>{r.status.toLowerCase()}</span></td>
              <td className="actions">
                {r.status === 'CONFIRMED' && <button className="danger" onClick={() => cancel(r)}>Cancel</button>}
              </td>
            </tr>
          ))}
          {data?.content.length === 0 && <tr><td colSpan={6} className="muted">No reservations yet</td></tr>}
        </tbody>
      </table>
      </div>
      {data && <Pager page={data.page} onChange={setPageNumber} />}
    </>
  )
}
