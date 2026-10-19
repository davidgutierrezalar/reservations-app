# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Slotline: a resource reservation system. `backend/` is a Spring Boot 4 / Java 25 API on PostgreSQL; `frontend/` is a React 19 + TypeScript SPA (Vite). The UI text and code are in English.

## Commands

```bash
docker compose up -d --build                     # full stack: app :3000, API on 127.0.0.1:8080 (Swagger at /swagger-ui.html); Postgres is not published
docker compose up -d --build --no-deps frontend  # rebuild only the frontend container
docker compose down                              # stop (keeps the db-data volume)
```

Demo admin in Compose: `admin@reservas.dev` / `admin12345`.

Backend (from `backend/`; on Windows use `mvnw.cmd`). Docker must be running: integration tests use Testcontainers.

```bash
./mvnw verify                                                   # what CI runs; coverage report in target/site/jacoco/
./mvnw test -Dtest=ReservationServiceTest                       # unit tests only (no Docker needed)
./mvnw test -Dtest=ApiIntegrationTest#editingKeepsTheSameRules  # a single test
./mvnw spring-boot:test-run                                     # run the API locally with a throwaway Postgres container
```

Frontend (from `frontend/`): `npm run dev` (port 5173, proxies `/api` to `:8080`), `npm run build` (runs `tsc -b`), `npm run lint` (oxlint). There are no frontend tests.

CI (`.github/workflows/ci.yml`) runs backend `verify`, frontend lint and build, then `docker compose build`.

## Backend architecture

There is one package per feature (`auth`, `resource`, `reservation`, `audit`, `user`) holding its own controller, service, repository and entity. Request and response DTOs are nested records inside a `*Dtos` holder class (e.g. `ReservationDtos.ReservationRequest`), each with a static `from(entity)` mapper.

**Authorization is split across two places:**
- **Role rules:** URL-based, in `config/SecurityConfig`. `GET /api/reservations`, approve/reject, `/api/audit/**` and any non-GET `/api/resources/**` require ADMIN.
- **Ownership checks:** in the services. Controllers turn the `JwtAuthenticationToken` into `auth.CurrentUser(id, admin)` and pass it to the service. Someone else's reservation returns 404, not 403, on purpose.

**Double-booking protection has two layers that must stay in sync:**
1. The service checks for overlaps (`existsBy...StatusIn...IdNot`, where `excludeId = 0` means none) so it can return a clear 409.
2. A Postgres exclusion constraint (`reservations_no_overlap`, latest in `V2__approval_series_audit.sql`) blocks overlaps on INSERT and UPDATE, even under concurrent requests.

Its `WHERE status IN ('PENDING','CONFIRMED')` must match `ReservationStatus.ACTIVE`: pending requests hold their slot. Writes use `saveAndFlush`/`flush` so a constraint violation surfaces inside the request. `GlobalExceptionHandler` maps `DataIntegrityViolationException` to 409.

**Errors:** every error leaves the API as an RFC 9457 `ProblemDetail`. Throw `common.ApiException` (`notFound`/`conflict`/`badRequest`). Use `.with(key, value)` to add machine-readable fields; series creation uses it to return `conflicts`.

**Schema:** Flyway owns the schema and Hibernate only runs `ddl-auto=validate`. Any entity change needs a new `V{n}__*.sql` migration; never edit an applied one.

**Audit:** `AuditService.record(...)` uses `Propagation.MANDATORY`, so call it from inside a `@Transactional` service method. The audit entry and the change commit together.

**Other conventions:**
- **Pages:** serialize as `{content, page: {size, number, totalElements, totalPages}}`, set up by `@EnableSpringDataWebSupport(VIA_DTO)` in `BackendApplication`. Search filters are built as a single `Specification` lambda that collects predicates.
- **Booking rules** (`ReservationService`): start in the future, end after start, at most 12h. Series are DAILY/WEEKLY, 2 to 12 dates, all or nothing. A user's booking on a `requiresApproval` resource starts PENDING; an admin's is CONFIRMED, and an admin's edit keeps the current status.
- **Auth cookies:** `AuthController` puts the access token (`app.jwt.ttl`, 15 min, path `/api`) and refresh token (`app.jwt.refresh-ttl`, 7 days, path `/api/auth/refresh`) in httpOnly, Secure, SameSite=Strict cookies; bodies only carry the user. Tokens carry `token_use` (`access`/`refresh`) and each `JwtDecoder` bean accepts only its own kind. A filter after `CsrfFilter` turns the access cookie into a bearer header: it must run after it, because the resource server exempts bearer requests from CSRF. CSRF uses `csrf.spa()`; `/api/auth/**` and requests with an `Authorization` header (tests, API clients) are exempt.
- **Rate limiting:** `auth.AuthRateLimitFilter` caps login and register attempts per IP (`app.auth.rate-limit.*`), in memory. `ApiIntegrationTest` raises the cap because all its requests share one IP; the filter has its own unit test. Behind nginx the IP comes from `X-Forwarded-For` (`server.forward-headers-strategy=native`), which `nginx.conf` overwrites with `$remote_addr`.
- **Config:** `app.jwt.secret` (env `JWT_SECRET`, at least 32 bytes) has no default, and the app refuses to start without it. Tests pass it through `@SpringBootTest(properties=...)`; `TestBackendApplication` sets dev values. `ADMIN_EMAIL`/`ADMIN_PASSWORD` seed the first admin (`AdminInitializer`).
- **Spring Boot 4 / Jackson 3 gotchas:**
  - A primitive field in a request record that the JSON omits fails with 400. Use a boxed type and normalize it in the compact constructor, as `ResourceRequest.requiresApproval` does.
  - `PropertyReferenceException` now lives in `org.springframework.data.core`.

## Tests

- `ReservationServiceTest`: Mockito unit tests of the booking rules.
- `ApiIntegrationTest`: drives MockMvc, built manually with `springSecurity()`, against a real Postgres. All tests share one database and nothing is cleaned between them. Keep new tests independent:
  - use unique resource names (UUID tags);
  - book on a distinct day offset with `futureHour(n)`.

## Frontend architecture

- **API client:** `src/api.ts` holds the API types and the only fetch wrapper `api<T>()`. It sends the `X-XSRF-TOKEN` header on writes, and on a 401 calls `refreshSession()` once (shared by parallel calls) and replays the request, else redirects to `/login`. It turns `ProblemDetail` into `ApiError` (`fieldErrors`, plus `data` for extra fields like `conflicts`). `errorText()` formats it for display.
- **Same origin, no CORS:** `/api` goes through Vite's dev proxy or nginx (`frontend/nginx.conf`) in Docker. Send times as UTC ISO strings (`toISOString()`) and display them in local time.
- **Session:** `AuthProvider` renders nothing until `POST /api/auth/refresh` answers, since httpOnly cookies are the only record of who is signed in.
- **Routing:** set up in `App.tsx`. The landing page (`/`) has its own layout. Signed-in pages share `AppLayout`, and `RequireAuth admin` guards the admin pages: Approvals reuses `ReservationsPage approvals`, Activity is separate.
- **Booking:** `ReservationForm` handles create, series and edit. It combines `Availability`, a week strip plus half-hour grid fed by `GET /api/resources/{id}/availability`, with `DateTimePicker`, which closes once a time is picked.
- **Notifications:** `EmailPreviewPage` is a visual mockup only. Nothing sends email, by decision.
- **Styling:** plain CSS in `src/index.css` with design tokens in `:root`. Each neon color has a role: magenta means action, cyan means info and focus, amber means selection or waiting.
  - Panels use `clip-path` cut corners, which also clip children. A panel that hosts a popover needs `.has-popover`.
  - Buttons show focus with an inset box-shadow, because `clip-path` clips outlines.
  - Respect `prefers-reduced-motion`.
- **Render purity:** oxlint flags `new Date()` and `Date.now()` during render. Capture them once with `useState(() => new Date())`.

## Repo notes

- `.gitattributes` forces LF, because `mvnw` runs inside Linux containers. Docker and CI call it as `sh mvnw` (the Windows checkout loses the executable bit).
- Work on feature branches and open a PR to `main`. Do not push directly to `main`.
