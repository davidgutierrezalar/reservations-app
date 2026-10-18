import { useId, type ReactNode } from 'react'
import type { ResourceType } from '../api'

// Hand-drawn neon line art, one per resource type; no external images to license or host
const SKYLINE = [
  [0, 150, 60], [55, 110, 45], [95, 170, 70], [160, 90, 50], [205, 140, 40], [240, 60, 65], [300, 125, 55],
  [350, 75, 40], [385, 160, 60], [440, 100, 45], [480, 50, 70], [545, 130, 50], [590, 85, 60], [645, 150, 45],
  [685, 70, 55], [735, 120, 65],
] as const

function Backdrop({ hue, children }: { hue: string; children: ReactNode }) {
  // Unique per instance: every slide is in the DOM at once and SVG ids are document-global
  const id = useId()
  return (
    <>
      <defs>
        <linearGradient id={`sky${id}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#1d1145" />
          <stop offset="0.6" stopColor="#120c2a" />
          <stop offset="1" stopColor="#0d0a1a" />
        </linearGradient>
        <filter id={`glow${id}`} x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="3" result="b" />
          <feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>
      </defs>
      <rect width="800" height="450" fill={`url(#sky${id})`} />
      <circle cx="620" cy="70" r="38" fill={hue} opacity="0.12" />
      {SKYLINE.map(([x, h, w]) => (
        <g key={x}>
          <rect x={x} y={260 - h} width={w} height={h} fill="#150f30" />
          {Array.from({ length: Math.floor(h / 22) }, (_, i) => (
            <rect key={i} x={x + 8 + ((i * 13) % (w - 16))} y={268 - h + i * 20} width="6" height="4"
                  fill={i % 3 ? '#ffb627' : '#19e6ff'} opacity="0.55" />
          ))}
        </g>
      ))}
      {/* Perspective floor grid converging on the horizon */}
      <g stroke="#19e6ff" strokeOpacity="0.28" strokeWidth="1">
        {Array.from({ length: 17 }, (_, i) => <line key={i} x1="400" y1="260" x2={-400 + i * 100} y2="450" />)}
        {[268, 282, 302, 330, 368, 418].map((y) => <line key={y} x1="0" y1={y} x2="800" y2={y} />)}
      </g>
      <rect y="258" width="800" height="3" fill={hue} opacity="0.7" />
      <g filter={`url(#glow${id})`} fill="none" strokeLinecap="round" strokeLinejoin="round" strokeWidth="3">
        {children}
      </g>
    </>
  )
}

const MeetingRoom = () => (
  <Backdrop hue="#ff2e88">
    <rect x="250" y="120" width="300" height="120" stroke="#19e6ff" />
    <path d="M275 215 l40 -50 l40 25 l50 -45 l45 30 l55 -30" stroke="#19e6ff" strokeWidth="2" />
    <path d="M220 330 L580 330 L640 400 L160 400 Z" stroke="#ff2e88" />
    {[250, 340, 430, 520].map((x) => <path key={x} d={`M${x} 330 v-34 h32 v34`} stroke="#ff2e88" strokeWidth="2" />)}
    {[200, 330, 470, 600].map((x) => <path key={x} d={`M${x} 405 v26 h34 v-26`} stroke="#ffb627" strokeWidth="2" />)}
  </Backdrop>
)

const Desk = () => (
  <Backdrop hue="#19e6ff">
    <path d="M190 330 H610 M220 330 V420 M580 330 V420" stroke="#19e6ff" />
    <rect x="300" y="200" width="180" height="110" stroke="#ff2e88" />
    <path d="M390 310 v20 M350 330 h80" stroke="#ff2e88" />
    <path d="M318 280 h60 M318 260 h100 M318 240 h80" stroke="#19e6ff" strokeWidth="2" />
    <path d="M540 330 v-90 l-40 -40 M480 214 l40 -28 l20 18" stroke="#ffb627" />
    <path d="M478 214 l-26 40 h70 z" stroke="#ffb627" strokeWidth="2" />
    <path d="M240 420 v-60 h60 v60 M250 360 v-40 h40 v40" stroke="#ff2e88" strokeWidth="2" />
  </Backdrop>
)

const Equipment = () => (
  <Backdrop hue="#ffb627">
    <rect x="300" y="200" width="160" height="96" stroke="#ffb627" />
    <circle cx="480" cy="248" r="34" stroke="#19e6ff" />
    <circle cx="480" cy="248" r="16" stroke="#19e6ff" strokeWidth="2" />
    <rect x="330" y="182" width="50" height="18" stroke="#ffb627" strokeWidth="2" />
    <path d="M380 296 L310 420 M380 296 L380 420 M380 296 L450 420" stroke="#ff2e88" />
    <path d="M514 248 L700 190 M514 248 L700 310" stroke="#19e6ff" strokeOpacity="0.6" strokeWidth="2" />
    <circle cx="330" cy="222" r="4" fill="#ff2e88" stroke="#ff2e88" />
  </Backdrop>
)

const Vehicle = () => (
  <Backdrop hue="#ff2e88">
    <path d="M150 360 L180 310 Q200 290 250 288 L330 286 L390 240 Q410 228 450 228 L540 228 Q570 228 590 250 L630 290 Q680 296 690 330 L690 360 Z" stroke="#ff2e88" />
    <path d="M405 250 L445 245 L535 245 L575 286 L355 286 Z" stroke="#19e6ff" strokeWidth="2" />
    <circle cx="260" cy="365" r="32" stroke="#19e6ff" />
    <circle cx="590" cy="365" r="32" stroke="#19e6ff" />
    <path d="M60 330 H140 M30 350 H120 M80 312 H160" stroke="#ffb627" strokeWidth="2" />
    <path d="M680 318 h14" stroke="#ffb627" strokeWidth="6" />
  </Backdrop>
)

const SCENES: Record<ResourceType, () => ReactNode> = {
  MEETING_ROOM: MeetingRoom,
  DESK: Desk,
  EQUIPMENT: Equipment,
  VEHICLE: Vehicle,
}

export default function Scene({ type }: { type: ResourceType }) {
  const Drawing = SCENES[type]
  return <Drawing />
}
