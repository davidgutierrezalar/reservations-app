import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import Carousel, { type Slide } from '../landing/Carousel'

const SLIDES: Slide[] = [
  { type: 'MEETING_ROOM', title: 'Meeting rooms', text: 'Rooms with a wall screen for 4 to 12 people. Book them by the half hour.' },
  { type: 'DESK', title: 'Hot desks', text: 'A desk, a monitor and a lamp for a morning, an afternoon or the whole day.' },
  { type: 'EQUIPMENT', title: 'Equipment', text: 'Cameras, projectors and audio gear you can take for a shoot or a demo.' },
  { type: 'VEHICLE', title: 'Vehicles', text: 'Shared cars for visits to clients, with up to 12 hours per booking.' },
]

const STEPS = [
  { title: 'Sign in', text: 'Create an account with your email. It takes less than a minute.' },
  { title: 'Pick a space and a time', text: 'Filter by type, choose a day and a start time.' },
  { title: 'It is yours', text: 'Once confirmed, nobody else can book that space at that time.' },
]

export default function LandingPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const book = () => navigate(user ? '/resources' : '/login')

  return (
    <>
      <header className="landing-top">
        <Link to="/" className="brand">Slotline</Link>
        {user
          ? <Link to="/resources">Go to your spaces</Link>
          : <Link to="/login">Sign in</Link>}
      </header>
      <main>
        <section className="hero">
          <div className="hero-copy">
            <h1 className="neon-title">Slotline</h1>
            <p>
              Book meeting rooms, desks, equipment and vehicles across the hub. Pick a time, confirm it, and the space
              is held for you. No double bookings.
            </p>
            <div className="hero-actions">
              <button className="big" onClick={book}>Book a space</button>
              {!user && <Link to="/login?mode=register">Create an account</Link>}
            </div>
          </div>
          <Carousel slides={SLIDES} />
        </section>
        <ol className="steps" aria-label="How booking works">
          {STEPS.map((s) => (
            <li key={s.title}>
              <h3>{s.title}</h3>
              <p>{s.text}</p>
            </li>
          ))}
        </ol>
      </main>
    </>
  )
}
