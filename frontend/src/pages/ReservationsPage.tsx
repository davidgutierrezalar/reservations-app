import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  api, formatDate, isActive, query, RESERVATION_STATUSES, type Page, type Reservation, type ReservationStatus,
} from '../api'
import { useAuth } from '../auth'
import Pager from '../Pager'
import ReservationForm from '../ReservationForm'

/** My reservations for users, every reservation for admins; with `approvals`, only pending requests. */
export default function ReservationsPage({ approvals = false }: { approvals?: boolean }) {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const [status, setStatus] = useState<ReservationStatus | ''>(approvals ? 'PENDING' : '')
  const [pageNumber, setPageNumber] = useState(0)
  const [data, setData] = useState<Page<Reservation> | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [editing, setEditing] = useState<Reservation | null>(null)
  const [now] = useState(() => Date.now())

  const load = useCallback(() => {
    const path = isAdmin ? '/reservations' : '/reservations/me'
    const sort = approvals ? 'startTime,asc' : undefined
    api<Page<Reservation>>(`${path}?${query({ status, page: pageNumber, size: 10, sort })}`)
      .then((p) => { setData(p); setError('') }, (e: Error) => setError(e.message))
  }, [isAdmin, status, pageNumber, approvals])

  useEffect(load, [load])

  const act = async (path: string, done: string, question?: string) => {
    if (question && !confirm(question)) return
    try {
      await api(path, { method: 'PATCH' })
      setNotice(done)
      load()
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <>
      <h1>{approvals ? 'Approvals' : isAdmin ? 'All reservations' : 'My reservations'}</h1>
      {approvals
        ? <p className="muted">Requests on resources that need approval. They hold their slot until you decide.</p>
        : (
          <div className="filters">
            <label>Status
              <select value={status} onChange={(e) => { setStatus(e.target.value as ReservationStatus | ''); setPageNumber(0) }}>
                <option value="">All statuses</option>
                {RESERVATION_STATUSES.map((s) => <option key={s} value={s}>{s.toLowerCase()}</option>)}
              </select>
            </label>
            <Link to="/email-previews" className="filters-link">See the emails users get</Link>
          </div>
        )}
      {error && <p className="error" role="alert">{error}</p>}
      {notice && <p className="success" role="status">{notice}</p>}

      {editing && (
        <ReservationForm key={editing.id} existing={editing}
                         resource={{ id: editing.resourceId, name: editing.resourceName }}
                         onSaved={() => { setEditing(null); setNotice('Changes saved. The table shows the new status.'); load() }}
                         onClose={() => setEditing(null)} />
      )}

      <div className="table-wrap">
        <table>
          <thead>
            <tr><th>Resource</th>{isAdmin && <th>User</th>}<th>Start</th><th>End</th><th>Status</th><th /></tr>
          </thead>
          <tbody>
            {data?.content.map((r) => {
              const upcoming = new Date(r.startTime).getTime() > now
              const when = `${r.resourceName} on ${formatDate(r.startTime)}`
              return (
                <tr key={r.id}>
                  <td>
                    {r.resourceName}
                    {r.seriesId && <span className="tag" title="Part of a recurring booking">repeats</span>}
                  </td>
                  {isAdmin && <td>{r.userName}</td>}
                  <td>{formatDate(r.startTime)}</td>
                  <td>{formatDate(r.endTime)}</td>
                  <td><span className={`badge ${r.status.toLowerCase()}`}>{r.status.toLowerCase()}</span></td>
                  <td className="actions">
                    {isAdmin && r.status === 'PENDING' && (
                      <>
                        <button onClick={() => act(`/reservations/${r.id}/approve`, `Approved ${when}.`)}>Approve</button>
                        <button className="danger"
                                onClick={() => act(`/reservations/${r.id}/reject`, `Rejected ${when}.`, `Reject ${when}?`)}>
                          Reject
                        </button>
                      </>
                    )}
                    {!approvals && isActive(r.status) && upcoming && (
                      <button className="secondary" onClick={() => setEditing(r)}>Change</button>
                    )}
                    {!approvals && isActive(r.status) && (
                      <button className="danger"
                              onClick={() => act(`/reservations/${r.id}/cancel`, `Cancelled ${when}.`, `Cancel ${when}?`)}>
                        Cancel
                      </button>
                    )}
                    {!approvals && r.seriesId && isActive(r.status) && (
                      <button className="danger"
                              onClick={() => act(`/reservations/series/${r.seriesId}/cancel`, 'Cancelled every upcoming date of the series.',
                                `Cancel every upcoming date of the ${r.resourceName} series? Past dates stay in your history.`)}>
                        Cancel series
                      </button>
                    )}
                  </td>
                </tr>
              )
            })}
            {data?.content.length === 0 && (
              <tr><td colSpan={6} className="muted">
                {approvals ? 'Nothing waiting for approval.' : <>No reservations here yet. <Link to="/resources">Book a space</Link>.</>}
              </td></tr>
            )}
          </tbody>
        </table>
      </div>
      {data && <Pager page={data.page} onChange={setPageNumber} />}
    </>
  )
}
