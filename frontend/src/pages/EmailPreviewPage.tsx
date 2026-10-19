import { useEffect, useState } from 'react'
import { api, formatDate, formatTime, type Page, type Reservation } from '../api'
import { useAuth } from '../auth'

// Mockup only: nothing here sends email. It shows what each message would say and when it would go out.
const SAMPLE: Pick<Reservation, 'id' | 'resourceName' | 'startTime' | 'endTime' | 'status'> = {
  id: 0,
  resourceName: 'Sala Bogota',
  startTime: new Date(Date.UTC(2026, 10, 3, 15, 0)).toISOString(),
  endTime: new Date(Date.UTC(2026, 10, 3, 16, 0)).toISOString(),
  status: 'CONFIRMED',
}

export default function EmailPreviewPage() {
  const { user } = useAuth()
  const [reservation, setReservation] = useState(SAMPLE)
  const [usingSample, setUsingSample] = useState(true)

  useEffect(() => {
    api<Page<Reservation>>('/reservations/me?size=1').then((p) => {
      if (p.content[0]) {
        setReservation(p.content[0])
        setUsingSample(false)
      }
    }, () => undefined)
  }, [])

  const when = `${formatDate(reservation.startTime)} to ${formatTime(reservation.endTime)}`
  const to = user?.email ?? 'you@example.com'
  const name = user?.name ?? 'there'
  const pending = reservation.status === 'PENDING'

  const emails = [
    {
      trigger: 'Right after booking',
      subject: pending ? `Request received: ${reservation.resourceName}` : `Booked: ${reservation.resourceName}, ${formatDate(reservation.startTime)}`,
      body: pending
        ? [`Hi ${name},`, `We got your request for ${reservation.resourceName}, ${when}.`, 'This space needs an admin to approve it. The slot is held for you meanwhile, and we will write again once it is decided.']
        : [`Hi ${name},`, `${reservation.resourceName} is yours, ${when}.`, 'Need a different time? Change or cancel it from My reservations.'],
      action: 'Open my reservations',
    },
    {
      trigger: '1 hour before it starts',
      subject: `Starts in 1 hour: ${reservation.resourceName}`,
      body: [`Hi ${name},`, `A reminder that ${reservation.resourceName} is booked for you at ${formatTime(reservation.startTime)}.`, 'Not going to make it? Cancel it so someone else can use the space.'],
      action: 'Cancel this booking',
    },
    {
      trigger: 'When it is cancelled or rejected',
      subject: `Cancelled: ${reservation.resourceName}, ${formatDate(reservation.startTime)}`,
      body: [`Hi ${name},`, `Your booking of ${reservation.resourceName}, ${when}, was cancelled.`, 'The slot is free again. You can book another time whenever you like.'],
      action: 'Book another time',
    },
  ]

  return (
    <>
      <h1>Email previews</h1>
      <p className="notice">
        This is a preview. Slotline does not send emails yet; these are the three messages a user would get, filled in
        with {usingSample ? 'sample data' : 'your latest reservation'}.
      </p>
      <ol className="email-flow">
        {emails.map((e) => (
          <li key={e.trigger}>
            <h2 className="email-trigger">{e.trigger}</h2>
            <article className="email" aria-label={`Email: ${e.subject}`}>
              <dl className="email-head">
                <dt>From</dt><dd>Slotline &lt;bookings@slotline.example&gt;</dd>
                <dt>To</dt><dd>{to}</dd>
                <dt>Subject</dt><dd><strong>{e.subject}</strong></dd>
              </dl>
              <div className="email-body">
                <p className="email-brand">Slotline</p>
                {e.body.map((line) => <p key={line}>{line}</p>)}
                <span className="email-button">{e.action}</span>
              </div>
            </article>
          </li>
        ))}
      </ol>
    </>
  )
}
