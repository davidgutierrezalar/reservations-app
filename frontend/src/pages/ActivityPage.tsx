import { useCallback, useEffect, useState } from 'react'
import { api, formatDate, query, type AuditEntry, type Page } from '../api'
import Pager from '../Pager'

type EntityType = AuditEntry['entityType']

/** Admin-only audit trail: who changed which reservation or resource, and when. */
export default function ActivityPage() {
  const [entityType, setEntityType] = useState<EntityType | ''>('')
  const [entityId, setEntityId] = useState('')
  const [pageNumber, setPageNumber] = useState(0)
  const [data, setData] = useState<Page<AuditEntry> | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    api<Page<AuditEntry>>(`/audit?${query({ entityType, entityId, page: pageNumber, size: 20 })}`)
      .then((p) => { setData(p); setError('') }, (e: Error) => setError(e.message))
  }, [entityType, entityId, pageNumber])

  useEffect(load, [load])

  return (
    <>
      <h1>Activity</h1>
      <div className="filters">
        <label>Showing
          <select value={entityType} onChange={(e) => { setEntityType(e.target.value as EntityType | ''); setPageNumber(0) }}>
            <option value="">Everything</option>
            <option value="RESERVATION">Reservations</option>
            <option value="RESOURCE">Resources</option>
          </select>
        </label>
        <label>Id
          <input type="number" min={1} placeholder="Any" value={entityId}
                 onChange={(e) => { setEntityId(e.target.value); setPageNumber(0) }} />
        </label>
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="table-wrap">
        <table>
          <thead><tr><th>When</th><th>Who</th><th>What</th><th>Details</th></tr></thead>
          <tbody>
            {data?.content.map((e) => (
              <tr key={e.id}>
                <td>{formatDate(e.createdAt)}</td>
                <td>{e.actorName}</td>
                <td>
                  <span className={`badge action-${e.action.toLowerCase()}`}>{e.action.toLowerCase()}</span>{' '}
                  {e.entityType.toLowerCase()} #{e.entityId}
                </td>
                <td className="muted">{e.details}</td>
              </tr>
            ))}
            {data?.content.length === 0 && <tr><td colSpan={4} className="muted">No changes recorded for this filter.</td></tr>}
          </tbody>
        </table>
      </div>
      {data && <Pager page={data.page} onChange={setPageNumber} />}
    </>
  )
}
