# Legal review checklist

Items marked **(verify current status)** change often. Confirm them with a web search before reporting.

## 1. Accessibility

Standard to test against: **WCAG 2.2 level AA**. It is the reference for all the laws below.

- **Colombia:** Ley 1618 de 2013 covers the rights of people with disabilities, including access to information. Resolución 1519 de 2020 (MinTIC) makes WCAG compliance mandatory for **public entities'** websites; private apps use it as the reference. **(verify current status)**
- **EU:** the European Accessibility Act (Directive 2019/882) has applied since **28 June 2025** to e-commerce and consumer services offered in the EU. Micro-enterprises are exempt for services.
- **US:** ADA Title III case law treats public websites of businesses as covered; WCAG 2.x AA is the usual benchmark.

What to check in the code:
- **Forms:** every input has a visible `<label>`. Errors are linked to the field and announced (`role="alert"` or `aria-describedby`).
- **Contrast:** at least 4.5:1 for text and 3:1 for UI components and focus rings. Check the muted text, the neon colors on dark panels, and disabled or "past" slots, which use `opacity`.
- **Keyboard:** everything is reachable by keyboard, with a visible focus everywhere. `clip-path` hides outlines, so buttons rely on inset box-shadows. Popovers close on Esc and return focus.
- **Color is not the only signal:** booked, pending and free slots also differ by text, pattern or `aria-label`.
- **Motion:** `prefers-reduced-motion` is respected (neon flicker, carousel autoplay). Autoplay can be paused (WCAG 2.2.2).
- **Images:** SVG illustrations have `role="img"` and an `aria-label`. The page sets `lang`.
- **Accessibility statement:** a page that states the conformance level, known gaps and a contact for issues. It is expected under the EU Accessibility Act and good practice elsewhere.

## 2. Use of AI

- **EU AI Act, Article 50:** transparency obligations apply from **2 August 2026**. The Digital Omnibus on AI (in force 27 July 2026) did not delay them. It only gave the machine-readable marking duty (Art. 50(2)) until 2 December 2026 for systems already on the market. *Checked October 2026.* **(verify current status)**
  - Users must be told when they interact with an AI system, such as a chatbot.
  - AI-generated or AI-manipulated content (images, audio, video, text published to inform the public) must be marked as such.
  - Code written with AI assistance is not itself covered. A booking app with no AI features has no Art. 50 duty today. If an AI assistant is added (for example "book with natural language"), the disclosure becomes mandatory for EU users.
- **Colombia:** there is no AI-specific law in force. As of September 2026, PL 025/2026 Cámara was in its first debate. Ley 1581 and SIC Circular 002 de 2024 already apply to AI that processes personal data. CONPES 4144 (2025) is a policy and does not bind private companies. *Checked October 2026.* **(verify current status)**
- **Copyright:** purely AI-generated material may not be protected by copyright. The US Copyright Office (Part 2 report, January 2025) requires human authorship. A human must select, arrange and modify the material for it to be protectable. Mention this when the user wants to license or sell the code.
- **Provider terms:** Anthropic's terms assign rights in outputs to the user. Confirm the current consumer or commercial terms for the plan in use. **(verify current status)**
- **Honesty with recruiters and users:** there is no legal duty for a portfolio, but recommend a short line in the README such as "Built with assistance from Claude Code (AI); design, review and decisions by <author>". Commits already carry `Co-Authored-By: Claude`; keep the README consistent with that.

## 3. Personal data and privacy

The app stores name, email, a password hash, bookings (who used what and when), and an audit log that names the actor.

- **Colombia (Ley 1581 de 2012, Decreto 1377 de 2013, compiled in Decreto 1074 de 2015):**
  - **Prior, express, informed authorization** is required before collecting data. That means a checkbox at registration linked to the policy, not pre-ticked.
  - A **Política de Tratamiento de Datos** must state the controller's identity and contact, purposes, data subject rights (know, update, rectify, delete, revoke) and the procedure for exercising them.
  - **Security principle:** this covers BCrypt passwords, secrets out of the repo and HTTPS in production. Security incidents must be reported to the SIC.
  - **RNBD registration** (Registro Nacional de Bases de Datos) applies only to companies above the asset threshold. It does not apply to a personal portfolio. **(verify current status)**
- **GDPR**, if EU users can register:
  - a lawful basis for processing;
  - a privacy notice (Art. 13);
  - data subject rights, including erasure;
  - a retention period;
  - international transfers.

  Note the conflict: the audit log is append-only, but erasure requests must still be handled, for example by anonymizing the actor.
- **Retention:** define how long reservations and audit entries are kept, and delete or anonymize them after that.
- **Demo data:** the demo must not ship with real people's data. Sample names and emails must be fictional.

## 4. Cookies, storage and third-party requests

- The JWT in `localStorage` is strictly necessary for the service, so it needs no consent banner under ePrivacy. Still mention it in the privacy policy.
- **Google Fonts loaded from fonts.googleapis.com** send every visitor's IP address to Google. A German court (LG München, 20 Jan 2022, 3 O 17493/20) held this a GDPR violation without consent. Fix: self-host the fonts (both are OFL-licensed) and drop the external `<link>`.
- No analytics or trackers are present today. Any added later need consent in the EU.

## 5. Licenses and assets

- **No `LICENSE` file means "all rights reserved".** Other people can view a public GitHub repo but cannot legally reuse it. For a portfolio, recommend MIT or Apache-2.0, and ask the user which.
- **Dependencies:** Spring and React are permissive (Apache-2.0 and MIT). Flag any GPL or AGPL dependency. Check with `./mvnw license:aggregate-third-party-report` or `npx license-checker --summary` only if the user agrees to run them.
- **Fonts:** Chakra Petch and Barlow are SIL Open Font License, so they are fine to use and to self-host.
- **Illustrations:** the carousel SVGs are original code in `landing/scenes.tsx`, so there are no third-party image rights. Re-check if photos or icons are added.

## 6. Name, trademarks and content

- **Name:** before any commercial use, search the name "Slotline" in the trademark registers: SIC (Colombia), USPTO, EUIPO and WIPO Global Brand Database. Report the result or "not checked".
- **Sample copy:** it must not claim real facilities, prices or capacity as if they were a real offer. If the site is public, add a "demo project" note.

## 7. Terms and consumer protection

These apply only if the app is offered to real users.
- Terms of use: covering acceptable use, cancellation rules, liability limits and the governing law.
- In Colombia, the Estatuto del Consumidor (Ley 1480 de 2011) applies when services are offered to consumers, especially paid ones.

## 8. Email (only when sending is implemented)

- Transactional emails (confirmation, reminder, cancellation) need no marketing consent. They must identify the sender and must not carry marketing.
- Any newsletter needs opt-in consent and an unsubscribe link. That means habeas data rules in Colombia and ePrivacy/GDPR in the EU.
