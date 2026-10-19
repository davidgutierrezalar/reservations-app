---
name: verify-change
description: Verify a change end to end in this repo before calling it done or opening a PR - backend tests with Testcontainers, frontend lint and build, the Docker Compose stack, and a browser check of the affected screens. Use after implementing a feature or fix, or when the user asks to test, verify or check that something works.
---

# Verify a change

Run the steps that apply to what changed. Report each one as passed, failed with its output, or skipped with the reason. Never say "works" for a step you did not run.

## 1. Preconditions

- Docker must be running: the integration tests and Compose need it. Check with `docker info --format '{{.ServerVersion}}'`. If it is down, start Docker Desktop on Windows and wait, or tell the user.
- Know which branch you are on (`git branch --show-current`). Work happens on feature branches, not `main`.

## 2. Backend (when anything under `backend/` changed)

```bash
cd backend && ./mvnw -B verify          # Windows: mvnw.cmd
```

- Read the totals from `target/surefire-reports/*.xml` (`tests=`, `failures=`, `errors=`). `-q` hides the summary.
- For new behavior, check that a test covers it.
  - **Business rules:** a unit test in `ReservationServiceTest`.
  - **Anything touching HTTP, security, JSON shape or the database:** a test in `ApiIntegrationTest`.
  - Integration tests share one database and nothing is cleaned between them. Use UUID-tagged names and a distinct `futureHour(n)` day offset.
- A request DTO that gained a field: check that old clients that omit it still work. Jackson 3 returns 400 for a missing primitive.

## 3. Frontend (when anything under `frontend/` changed)

```bash
cd frontend && npm run lint && npm run build
```

Both must finish with no warnings left unexplained. There are no frontend tests, so the browser check in step 5 is the test.

## 4. Stack

```bash
docker compose up -d --build                     # whole stack
docker compose up -d --build --no-deps frontend  # frontend-only change
```

- After a backend change, check the backend logs (`docker compose logs backend`) for `Successfully applied N migration` and `Started BackendApplication`.
- A 502 right after `up` usually means the backend is still starting. Retry after a few seconds before debugging.
- A new migration runs here against the existing `db-data` volume. That checks the migration against real data, which a fresh Testcontainers database cannot.

## 5. Browser

Open http://localhost:3000 in the built-in browser.
- **Admin:** use `admin@reservas.dev` / `admin12345`.
- **Regular user:** register a test account from the login screen, or create one through `/api/auth/register`.

Then check:
- **Each changed screen**, as both a user and an admin when roles matter. Admin-only UI must not appear for users, and the API must answer 403.
- **The error path,** not only the happy path. For example: an overlapping booking gives a 409 shown in the UI, and an invalid form shows field errors.
- **Mobile width (375px):** no horizontal page scroll. Check with `document.documentElement.scrollWidth`.
- **Panels with popovers:** a panel that hosts a popover (date picker) must not clip it. It needs `.has-popover`.
- **Keyboard:** focus is visible, and Esc closes popovers.

Reset any viewport emulation when done.

## 6. Report

End with:
- a short list of steps with passed, failed or skipped;
- what was not verified (for example: CI not run yet, touch devices, screen readers);
- whether the stack was left running. Stop it with `docker compose down` if the user is finished; the data volume survives.
