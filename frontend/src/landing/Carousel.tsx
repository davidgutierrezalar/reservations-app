import { useEffect, useState } from 'react'
import type { ResourceType } from '../api'
import Scene from './scenes'

export interface Slide { type: ResourceType; title: string; text: string }

const AUTOPLAY_MS = 6000

export default function Carousel({ slides }: { slides: Slide[] }) {
  const [index, setIndex] = useState(0)
  // Two reasons to stop: the viewer asked (button, or reduced motion), or is hovering/focusing for a moment
  const [stopped, setStopped] = useState(
    () => window.matchMedia('(prefers-reduced-motion: reduce)').matches,
  )
  const [hovered, setHovered] = useState(false)
  const paused = stopped || hovered

  useEffect(() => {
    if (paused) return
    const id = setInterval(() => setIndex((i) => (i + 1) % slides.length), AUTOPLAY_MS)
    return () => clearInterval(id)
  }, [paused, slides.length])

  const go = (i: number) => setIndex((i + slides.length) % slides.length)

  return (
    <section
      className="carousel"
      aria-roledescription="carousel"
      aria-label="Spaces you can book"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onFocus={() => setHovered(true)}
      onKeyDown={(e) => {
        if (e.key === 'ArrowRight') go(index + 1)
        if (e.key === 'ArrowLeft') go(index - 1)
      }}
    >
      {/* Announce slide changes only when the user drives them, not on every autoplay tick */}
      <div className="carousel-frame" aria-live={paused ? 'polite' : 'off'}>
        {slides.map((s, i) => (
            <div key={s.type} className={`carousel-slide${i === index ? ' active' : ''}`}
                 role="group" aria-roledescription="slide" aria-label={`${i + 1} of ${slides.length}`}
                 aria-hidden={i !== index}>
              <svg viewBox="0 0 800 450" role="img" aria-label={s.title} preserveAspectRatio="xMidYMid slice">
                <Scene type={s.type} />
              </svg>
            </div>
        ))}
      </div>
      <div className="carousel-caption">
        <h3>{slides[index].title}</h3>
        <p>{slides[index].text}</p>
      </div>
      <div className="carousel-controls">
        <div className="carousel-dots">
          {slides.map((s, i) => (
            <button key={s.type} className="carousel-dot" aria-label={`Show ${s.title}`}
                    aria-current={i === index} onClick={() => go(i)} />
          ))}
        </div>
        {/* WCAG 2.2.2: anything that moves on its own for more than 5 s needs a visible way to stop it */}
        <button className="secondary" aria-pressed={stopped} onClick={() => setStopped(!stopped)}>
          {stopped ? 'Play' : 'Pause'}
        </button>
        <button className="secondary" aria-label="Previous space" onClick={() => go(index - 1)}>Prev</button>
        <button className="secondary" aria-label="Next space" onClick={() => go(index + 1)}>Next</button>
      </div>
    </section>
  )
}
